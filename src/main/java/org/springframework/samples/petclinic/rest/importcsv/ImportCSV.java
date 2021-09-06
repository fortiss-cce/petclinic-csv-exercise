package org.springframework.samples.petclinic.rest.importcsv;

import jdk.javadoc.internal.doclets.toolkit.taglets.UserTaglet;
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

    private int currentPosition;

    @PreAuthorize("hasRole(@roles.OWNER_ADMIN)")
    @RequestMapping(value = "importPets",
        method = RequestMethod.POST,
        consumes = "text/plain",
        produces = "application/json")
    public ResponseEntity<List<Pet>> importPets(@RequestBody String csv) {

        this.currentPosition = 0;

        List<Pet> pets = new LinkedList<Pet>();
        Pet pet;
        boolean petInformationAvailable;

        do {
            pet = new Pet();

            setPetName(pet, csv);

            ResponseEntity<List<Pet>> invalidDate = setBirthDate(pet, csv);
            if (invalidDate != null) return invalidDate;

            setPetType(pet, csv);

            ResponseEntity<List<Pet>> invalidOwner = setPetOwner(pet, csv);
            if (invalidOwner != null) return invalidOwner;

            applyActionToPet(pet, csv);

            this.currentPosition++;

            pets.add(pet);

            petInformationAvailable = this.currentPosition < csv.length() && pet != null;
        } while (petInformationAvailable);

        return new ResponseEntity<List<Pet>>(pets, HttpStatus.OK);
    }

    private void removePetFromClinic(Pet pet) {
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

    private void applyActionToPet(Pet pet, String csv) {
        if (csv.charAt(this.currentPosition) == ';') {
            this.currentPosition++;

            String method = readNextField(csv);

            if (method.toLowerCase().equals("add")) {
                clinicService.savePet(pet);
            } else {
                removePetFromClinic(pet);
            }

        } else {
            clinicService.savePet(pet);
        }
    }

    private String readNextField(String csvString) {
        String field = "";
        while (this.currentPosition < csvString.length() && (csvString.charAt(this.currentPosition) != ';' && csvString.charAt(this.currentPosition) != '\n')) {
            field += csvString.charAt(this.currentPosition++);
        }
        return field;
    }

    private ResponseEntity<List<Pet>> generateErrorResponse(String errorMessage) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("errors", errorMessage);
        return new ResponseEntity<List<Pet>>(headers, HttpStatus.BAD_REQUEST);
    }

    private ResponseEntity<List<Pet>> setPetOwner(Pet pet, String csv) {
        String petOwner = readNextField(csv);

        if (pet != null) {
            String owner = petOwner;
            List<Owner> matchingOwners = getOwnerByLastName(owner);

            ResponseEntity<List<Pet>> ownerNotFound = checkOwnerInDatabase(matchingOwners);
            if (ownerNotFound != null) return ownerNotFound;

            ResponseEntity<List<Pet>> ownerNotUnique = checkOwnerIsUnique(matchingOwners);
            if (ownerNotUnique != null) return ownerNotUnique;

            pet.setOwner(matchingOwners.iterator().next());
        }
        return null;
    }

    private List<Owner> getOwnerByLastName(String lastName) {
        List<Owner> matchingOwners = clinicService.findAllOwners()
            .stream()
            .filter(o -> o.getLastName().equals(lastName))
            .collect(Collectors.toList());
        return matchingOwners;
    }

    private ResponseEntity<List<Pet>> checkOwnerInDatabase(List<Owner> owners) {
        if (owners.size() == 0) {
            return generateErrorResponse("Owner not found");
        }
        return null;
    }

    private ResponseEntity<List<Pet>> checkOwnerIsUnique(List<Owner> owners) {
        if (owners.size() > 1) {
            return generateErrorResponse("Owner not unique");
        }
        return null;
    }

    private void setPetName(Pet pet, String csv) {
        String petName = readNextField(csv);
        pet.setName(petName);
    }

    private ResponseEntity<List<Pet>> setBirthDate(Pet pet, String csv) {
        String birthDate = readNextField(csv);
        try {
            pet.setBirthDate((new SimpleDateFormat("yyyy-MM-dd")).parse(birthDate));
        } catch (ParseException e) {
            return generateErrorResponse("date " + birthDate + " not valid");
        }
        return null;
    }

    private void setPetType(Pet pet, String csv){
        String petType = readNextField(csv);

        if (pet != null) {
            ArrayList<PetType> ts = (ArrayList<PetType>) clinicService.findPetTypes();
            for (int j = 0; j < ts.size(); j++) {
                if (ts.get(j).getName().toLowerCase().equals(petType)) {
                    pet.setType(ts.get(j));
                    break;
                }
            }
        }
    }

}
