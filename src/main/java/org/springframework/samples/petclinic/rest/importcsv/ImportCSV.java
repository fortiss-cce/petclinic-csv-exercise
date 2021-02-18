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


    /**
     * Method for parsing the csv and give a HTTP reply to the REST
     *
     * @param csv the csv string from the user
     * @return ResponseEntity the HTTP response
     */
    @PreAuthorize("hasRole(@roles.OWNER_ADMIN)")
    @RequestMapping(value = "importPets",
        method = RequestMethod.POST,
        consumes = "text/plain",
        produces = "application/json")

    public ResponseEntity<List<Pet>> importPets(@RequestBody String csv) {

        List<Pet> pets = new LinkedList<Pet>();

        try{
            pets = ParseFields(csv);
        } catch (IncorrectFormatException e) {
            HttpHeaders headers = new HttpHeaders();
            headers.add("errors", e.getMessage());
            return new ResponseEntity<List<Pet>>(headers, HttpStatus.BAD_REQUEST);
        }

        return new ResponseEntity<List<Pet>>(pets, HttpStatus.OK);
    }

    /**
     * Custom Exception which can arise during csv string parsing, error message
     * contains where the exception was raised
     */
    private class IncorrectFormatException extends Exception {
        public IncorrectFormatException(String errorMessage) {
            super(errorMessage);
        }
    }

    /**
     * Method which receives a string from the csv and pars it to a list of pets
     * which is then returns. Exceptions are thrown upon the error field
     *
     * @param csv the csv string from the user
     * @return pets list of pets
     * @throws IncorrectFormatException the user input is malformatted
     */
    private List<Pet> ParseFields(String csv) throws IncorrectFormatException {
        int i = 0;
        List<Pet> pets = new LinkedList<Pet>();
        Pet pet;
        pet = new Pet();

        String[] arrayOfCsv = csv.split(";", 0);
        pet.setName(arrayOfCsv[0]);

        try {
            pet.setBirthDate((new SimpleDateFormat("yyyy-MM-dd")).parse(arrayOfCsv[1]));
        } catch (ParseException e) {
            throw new IncorrectFormatException("Date not valid");
        }

        ArrayList<PetType> petTypes = (ArrayList<PetType>) clinicService.findPetTypes();
        for (PetType petType : petTypes) {
            if (petType.getName().toLowerCase().equals(arrayOfCsv[2])) {
                pet.setType(petType);
                break;
            }
        }

        String owner = arrayOfCsv[3];
        List<Owner> matchingOwners = clinicService.findAllOwners()
            .stream()
            .filter(o -> o.getLastName().equals(owner))
            .collect(Collectors.toList());

        if (matchingOwners.size() == 0) {
            throw new IncorrectFormatException("Owner not found");
        }
        if (matchingOwners.size() > 1) {
            throw new IncorrectFormatException("Owner not unique");
        }
        pet.setOwner(matchingOwners.iterator().next());

        if (arrayOfCsv[4].toLowerCase().equals("add")) {
            clinicService.savePet(pet);
        } else {
            for (Pet queuePet : pet.getOwner().getPets()) {
                if (isSamePet(pet, queuePet)) {
                    clinicService.deletePet(queuePet);
                }
            }
        }

        pets.add(pet);

        return pets;
    }

    /**
     * Method to compare pets in the database and the inserted csv line
     *
     * @param pet the pet which need to be compare
     * @param queuePet the pet obtained from the database
     * @return isSamePet
     */
    private boolean isSamePet(Pet pet, Pet queuePet) throws IncorrectFormatException {
        boolean isSamePet = false;
        try {
            if (queuePet.getName().equals(pet.getName()) && queuePet.getType().getId()
                .equals(pet.getType().getId()) && pet.getBirthDate().equals(queuePet.getBirthDate())) {
                isSamePet = true;
            }
        } catch (NullPointerException nullpointer) {
            System.out.println("A pet is missing :(");
            throw new IncorrectFormatException("Pet not existing");
        }
        return isSamePet;
    }

}
