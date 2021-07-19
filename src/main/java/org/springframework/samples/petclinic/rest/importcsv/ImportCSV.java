package org.springframework.samples.petclinic.rest.importcsv;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.samples.petclinic.model.Owner;
import org.springframework.samples.petclinic.model.Pet;
import org.springframework.samples.petclinic.model.PetType;
import org.springframework.samples.petclinic.service.ClinicService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@CrossOrigin(exposedHeaders = "errors, content-type")
@RequestMapping("api/import")
public class ImportCSV {

    @Autowired
    private ClinicService clinicService;

    @PreAuthorize("hasRole(@roles.OWNER_ADMIN)")
    @RequestMapping(value = "importPets",
        method = RequestMethod.POST,
        consumes = "text/plain",
        produces = "application/json")
    public ResponseEntity<List<Pet>> importPets(@RequestBody String csv) {

        int i = 0;
        List<Pet> pets = new LinkedList<Pet>();
        Pet pet;

        do {
            pet = new Pet();


            String field = "";
            while (i < csv.length() && csv.charAt(i) != ';') {
                field += csv.charAt(i++);
            }
            i++;

            pet.setName(field);


            //
            field = "";
            while (i < csv.length() && csv.charAt(i) != ';') {
                field += csv.charAt(i++);
            }
            i++;

            try {
                pet.setBirthDate((new SimpleDateFormat("yyyy-MM-dd")).parse(field));
            } catch (ParseException e) {
                HttpHeaders headers = new HttpHeaders();
                headers.add("errors", "date " + field + " not valid");
                return new ResponseEntity<List<Pet>>(headers, HttpStatus.BAD_REQUEST);
            }

            field = "";
            while (i < csv.length() && csv.charAt(i) != ';') {
                field += csv.charAt(i++);
            }
            i++;

            if (pet != null) {
                ArrayList<PetType> ts = (ArrayList<PetType>) clinicService.findPetTypes();
                for (int j = 0; j < ts.size(); j++) {
                    if (ts.get(j).getName().toLowerCase().equals(field)) {
                        pet.setType(ts.get(j));
                        break;
                    }
                }
            }

            field = "";
            while (i < csv.length() && (csv.charAt(i) != ';' && csv.charAt(i) != '\n')) {
                field += csv.charAt(i++);
            }

            if (pet != null) {
                String owner = field;
                List<Owner> matchingOwners = clinicService.findAllOwners()
                    .stream()
                    .filter(o -> o.getLastName().equals(owner))
                    .collect(Collectors.toList());

                if (matchingOwners.size() == 0) {
                    HttpHeaders headers = new HttpHeaders();
                    headers.add("errors", "Owner not found");
                    return new ResponseEntity<List<Pet>>(headers, HttpStatus.BAD_REQUEST);
                }
                if (matchingOwners.size() > 1) {
                    HttpHeaders headers = new HttpHeaders();
                    headers.add("errors", "Owner not unique");
                    return new ResponseEntity<List<Pet>>(headers, HttpStatus.BAD_REQUEST);
                }
                pet.setOwner(matchingOwners.iterator().next());
            }

            if (csv.charAt(i) == ';') {
                i++;

                field = "";
                while (i < csv.length() && csv.charAt(i) != '\n') {
                    field += csv.charAt(i++);
                }

                if (field.toLowerCase().equals("add")) {
                    clinicService.savePet(pet);
                } else {
                    for (Pet q : pet.getOwner().getPets()) {
                        if (q.getName().equals(pet.getName())) {
                            if (q.getType().getId().equals(pet.getType().getId())) {
                                if (pet.getBirthDate().equals(q.getBirthDate())) {
                                    clinicService.deletePet(q);
                                }
                            }
                        }
                    }
                }

            } else {
                clinicService.savePet(pet);
            }
            i++;

            pets.add(pet);

        } while (i < csv.length() && pet != null);

        return new ResponseEntity<List<Pet>>(pets, HttpStatus.OK);
    }


    public ResponseEntity<List<Pet>> importPets2(@RequestBody String csv){
        List<PetAttribute> petAttributes = getPetAttributes(csv);

        List<Pet> pets = new LinkedList<>();

        List<String> petTypes = clinicService.findPetTypes().stream().map(pet -> pet.toString().toLowerCase()).collect(Collectors.toList());

        for (PetAttribute petAttribute : petAttributes){
            if (petAttribute.isPetAttributeValid()){
                Pet pet = new Pet();
                pet.setName(petAttribute.getName());
                try {
                    pet.setBirthDate(petAttribute.getBirthdayDateFormat());
                } catch (ParseException e) {
                    HttpHeaders headers = new HttpHeaders();
                    headers.add("errors", "date " + petAttribute.getBirthday() + " not valid");
                    return new ResponseEntity<List<Pet>>(headers, HttpStatus.BAD_REQUEST);
                }


                if (petTypes.contains(petAttribute.getAnimal().toLowerCase())){
                    //TODO add something here
                }

                //TODO add owner

                //TODO make it robust by only usind valid entries

            }

        }
        return new ResponseEntity<List<Pet>>(pets, HttpStatus.OK);
    }


    private List<PetAttribute> getPetAttributes(String csv){

        List<PetAttribute> petAttributes = new LinkedList<>();

        String[] lines = csv.split("\n");
        for (String line : lines){
            String[] entries = line.split(";");
            String name = entries[0];
            String birthday = entries[1];
            String animal = entries[2];
            String owner = entries[3];
            String operation = entries[4]; // add or delete
            petAttributes.add(new PetAttribute(name, birthday, animal, owner, operation));
        }
        return petAttributes;

    }




}
