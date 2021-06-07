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
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@CrossOrigin(exposedHeaders = "errors, content-type")
@RequestMapping("api/import")
public class ImportCSV {

    private static final String FIELD_DELIMITER = ";";
    private static final String LINE_DELIMITER = "\n";
    private static int NAME = 0;
    private static int BIRTH_DATE = 1;
    private static int PET_TYPE = 2;
    private static int OWNER = 3;
    private static int ACTION = 4;


    @Autowired
    private ClinicService clinicService;

    @PreAuthorize("hasRole(@roles.OWNER_ADMIN)")
    @RequestMapping(value = "importPets",
        method = RequestMethod.POST,
        consumes = "text/plain",
        produces = "application/json")
    public ResponseEntity<List<Pet>> importPets(@RequestBody String csv) {

        List<Pet> pets = new LinkedList<>();

        String[] lines = csv.split(LINE_DELIMITER);
        for (String line : lines) {
            String petString = line.trim();
            try {
                Pet pet = parsePetString(petString);
                pets.add(pet);
            } catch (ParseException e) {
                HttpHeaders headers = new HttpHeaders();
                headers.add("errors", e.getMessage());
                return new ResponseEntity<>(headers, HttpStatus.BAD_REQUEST);
            }
        }
        return new ResponseEntity<>(pets, HttpStatus.OK);
    }

    private Pet parsePetString(String petString) throws ParseException {
        Pet pet = new Pet();
        String[] fields = petString.split(FIELD_DELIMITER);

        if (fields.length != 5){
            String error = String.format("Wrong number of fields found. Expected 5 got %d", fields.length);
            throw new ParseException(error, -1);
        }

        pet.setName(fields[NAME].trim());

        parseBirthDateFiled(pet, fields[BIRTH_DATE].trim());

        parseTypeString(pet, fields[PET_TYPE].trim());

        parseOwner(pet, fields[OWNER].trim());

        String action = fields[ACTION].trim();
        if (action.toLowerCase().equals("add")){
            clinicService.savePet(pet);
        } else {
            deletePet(pet);
        }

        return pet;
    }

    private void parseOwner(Pet pet, String owner) throws ParseException{
        List<Owner> matchingOwners = clinicService.findAllOwners()
            .stream()
            .filter(o -> o.getLastName().equals(owner))
            .collect(Collectors.toList());

        if (matchingOwners.size() == 0) {
            throw new ParseException("Owner not found", -1);
        }

        if (matchingOwners.size() > 1) {
            throw new ParseException("Owner not unique", -1);
        }
        pet.setOwner(matchingOwners.iterator().next());
    }

    private void parseTypeString(Pet pet, String petTypeString) throws ParseException {
        Optional<PetType> petType = clinicService.findPetTypes()
            .stream()
            .filter(t -> t.getName().equals(petTypeString))
            .findFirst();
        if (!petType.isPresent()){
            throw new ParseException("PetType not found", -1);
        }
        pet.setType(petType.get());
    }

    private void parseBirthDateFiled(Pet pet, String dateFiled)  throws ParseException {
        try {
            pet.setBirthDate((new SimpleDateFormat("yyyy-MM-dd")).parse(dateFiled));
        }  catch (ParseException e) {
            throw new ParseException("date " + dateFiled + " not valid", -1);
        }
    }

    private void deletePet(Pet pet){
        List<Pet> allPetsOfOwner = pet.getOwner().getPets();
        allPetsOfOwner.forEach(ownedPet -> {
            if (comparePet(ownedPet, pet)){
                clinicService.deletePet(ownedPet);
            }
        });
    }

    private boolean comparePet(Pet pet1, Pet pet2){
        return  pet1.getName().equals(pet2.getName()) &&
                pet1.getType().getId().equals(pet2.getType().getId()) &&
                pet1.getBirthDate().equals(pet2.getBirthDate());
    }
}
