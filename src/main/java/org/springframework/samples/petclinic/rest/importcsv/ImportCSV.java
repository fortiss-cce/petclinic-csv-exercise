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

//removed wildcard import
import java.util.*;

import java.util.stream.Collectors;

/* This class implements the Petclionic Service in the backend.
* The input is a CSV format with semi-colons (;) as separator.
*/

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

/*            while (i < csv.length() && csv.charAt(i) != ';') {
                field += csv.charAt(i++);
            }
            i++;

            pet.setName(field);
*/
            /*field = "";
            while (i < csv.length() && csv.charAt(i) != ';') {
                field += csv.charAt(i++);
            }
            i++;*/
/*
            try {
                pet.setBirthDate((new SimpleDateFormat("yyyy-MM-dd")).parse(field));
            } catch (ParseException e) {
                HttpHeaders headers = new HttpHeaders();
                headers.add("errors", "date " + field + " not valid");
                return new ResponseEntity<List<Pet>>(headers, HttpStatus.BAD_REQUEST);
            }
*/
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



    private List<String> segment_csv_int_string_list(String csv)
    {
        List<String> entries;

        entries = Arrays.asList(csv.split(";"));

        return entries;
    }

    private Pet convert_string_list_to_pet( List<String> list)
    {
        Pet pet = new Pet();
        pet.setName(list.get(0));
        pet.setBirthDate(convert_birth_date_from_string(list.get(1)));

        return pet;
    }

    private Date convert_birth_date_from_string(String birth_date_string)
    {
        Date date = new Date();

        SimpleDateFormat simple_date_format = new SimpleDateFormat("yyyy-MM-dd");

        try {
            date = simple_date_format.parse(birth_date_string);
        }
        catch (ParseException e) {
            HttpHeaders headers = new HttpHeaders();
            headers.add("errors", "date " + birth_date_string + " not valid");

        }

        return date;

    }

    private ArrayList<PetType> get_pet_types_from_server()
    {
        ArrayList<PetType> pet_type_list = (ArrayList<PetType>) clinicService.findPetTypes();
        return pet_type_list;
    }

    private PetType search_for_pet_type_in_list(String entry)
    {
        PetType pet_type = new PetType();

        ArrayList<PetType> pet_type_list = get_pet_types_from_server();

        for (int j = 0; j < pet_type_list.size(); j++)
        {
            if (pet_type_list.get(j).getName().toLowerCase().equals(entry))
            {
                pet_type= pet_type_list.get(j);
                break;
            }
        }

        return pet_type;

    }


    /*
        ArrayList<PetType> ts = (ArrayList<PetType>) clinicService.findPetTypes();

    }
*/


}
