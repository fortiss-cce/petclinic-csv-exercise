package org.springframework.samples.petclinic.rest.importcsv;

import jdk.nashorn.internal.runtime.ParserException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.parsing.ParseState;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.samples.petclinic.model.Owner;
import org.springframework.samples.petclinic.model.Pet;
import org.springframework.samples.petclinic.model.PetType;
import org.springframework.samples.petclinic.service.ClinicService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.parameters.P;
import org.springframework.web.bind.annotation.*;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@CrossOrigin(exposedHeaders = "errors, content-type")
@RequestMapping("api/import")
public class ImportCSV {
    private enum PetAction {
        Add,
        Delete
    }

    @Autowired
    private ClinicService clinicService;

    // The csv data
    private String csv;
    // The current position in our csv input
    private int currentPosition;

    private boolean csvNotDone() { return currentPosition < csv.length(); }

    private String extractOptionalField() {
        if (csvNotDone() && (getCurrentChar() != '\n')) {
            return extractField();
        }
        return null;
    }
    private char getCurrentChar() {
        return csv.charAt(currentPosition);
    }
    private void consumeChar() { currentPosition++; }
    private void expectEndOfLine() throws ParseException {
        if (csvNotDone()) {
            if (getCurrentChar() != '\n') {
                throw new ParseException("Expected end-of-line", currentPosition);
            }
            consumeChar();
        }
    }
    private String extractField() {
        int fieldBegin = currentPosition;
        while (csvNotDone() && (getCurrentChar() != ';' && getCurrentChar() != '\n')) {
            consumeChar();
        }
        int fieldEnd = currentPosition;
        // Consume ';' immediately, but not '\n'. We consume that via `expectEndOfLine()`.
        if (csvNotDone() && getCurrentChar() == ';') {
            consumeChar();
        }
        return csv.substring(fieldBegin, fieldEnd);
    }
    private PetType validatePetType(String petType) throws ParseException {
        for (PetType type : clinicService.findPetTypes()) {
            if (type.getName().toLowerCase().equals(petType)) {
                return type;
            }
        }
        throw new ParseException("PetType not found", currentPosition);
    }
    private Owner validateOwner(String owner) throws ParseException {
        List<Owner> matchingOwners = clinicService.findAllOwners()
            .stream()
            .filter(o -> o.getLastName().equals(owner))
            .collect(Collectors.toList());

        if (matchingOwners.size() == 0) {
            throw new ParseException("Owner not found", currentPosition);
        }
        if (matchingOwners.size() > 1) {
            throw new ParseException("Owner not unique", currentPosition);
        }
        return matchingOwners.iterator().next();
    }
    private Date validateDate(String date) throws ParseException {
        try {
            return (new SimpleDateFormat("yyyy-MM-dd")).parse(date);
        } catch (ParseException e) {
            throw new ParseException("date " + date + " not valid", currentPosition);
        }
    }
    private Pet extractPet() throws ParseException {
        Pet result = new Pet();

        String name = extractField();
        result.setName(name);

        String birthDate = extractField();
        result.setBirthDate(validateDate(birthDate));

        String petType = extractField();
        result.setType(validatePetType(petType));

        String owner = extractField();
        result.setOwner(validateOwner(owner));

        return result;
    }
    private PetAction getAction() throws ParseException {
        String action = extractOptionalField();
        if (action == null) {
            // If there is no action specified, use "add" by default
            return PetAction.Add;
        } else if (action.toLowerCase().equals("add")) {
            return PetAction.Add;
        } else if (action.toLowerCase().equals("delete")) {
           return PetAction.Delete;
        } else {
            throw new ParseException("unknown action " + action, currentPosition);
        }
    }
    private boolean petExists(Pet pet) {
        for (Pet q : pet.getOwner().getPets()) {
            if (q.getName().equals(pet.getName()) && q.getType().getId().equals(pet.getType().getId()) && pet.getBirthDate().equals(q.getBirthDate())) {
                return true;
            }
        }
        return false;
    }
    private Pet extractPetAndPerformAction() throws ParseException {
        Pet pet = extractPet();
        PetAction action = getAction();
        try {
            switch (action) {
                case Add:
                    clinicService.savePet(pet);
                case Delete: {
                    if (petExists(pet)) {
                        clinicService.deletePet(pet);
                    }
                }
            }
        } catch (DataAccessException e) {
            throw new ParseException("Could not execute action", currentPosition);
        }
        expectEndOfLine();
        return pet;
    }
    @PreAuthorize("hasRole(@roles.OWNER_ADMIN)")
    @RequestMapping(value = "importPets",
        method = RequestMethod.POST,
        consumes = "text/plain",
        produces = "application/json")
    public ResponseEntity<List<Pet>> importPets(@RequestBody String csv) {
        this.csv = csv;
        this.currentPosition = 0;

        List<Pet> pets = new LinkedList<Pet>();
        while (csvNotDone()) {
            try {
                pets.add(extractPetAndPerformAction());
            } catch (ParseException e) {
                HttpHeaders headers = new HttpHeaders();
                headers.add("errors", e.getMessage());
                return new ResponseEntity<List<Pet>>(headers, HttpStatus.BAD_REQUEST);
            }
        }
        return new ResponseEntity<List<Pet>>(pets, HttpStatus.OK);
    }
}
