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
import java.util.Collection;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@CrossOrigin(exposedHeaders = "errors, content-type")
@RequestMapping("api/import")
public class ImportCSV {

    private final char fieldSeparator = ';';
    private final char lineSeparator = '\n';
    private final String dateFormat = "yyyy-MM-dd";
    @Autowired
    private ClinicService clinicService;

    @PreAuthorize("hasRole(@roles.OWNER_ADMIN)")
    @RequestMapping(value = "importPets",
        method = RequestMethod.POST,
        consumes = "text/plain",
        produces = "application/json")
    public ResponseEntity<List<Pet>> importPets(@RequestBody String csv) {
        List<Pet> pets = new LinkedList<Pet>();
        String[] lines = csv.split(String.valueOf(lineSeparator));
        for (String line : lines) {
            try {
                Pet pet = parsePetFromLine(line);
                pets.add(pet);
            } catch (ParseException e) {
                HttpHeaders headers = new HttpHeaders();
                headers.add("errors", e.getMessage());
                return new ResponseEntity<List<Pet>>(headers, HttpStatus.BAD_REQUEST);
            }
        }
        return new ResponseEntity<List<Pet>>(pets, HttpStatus.OK);
    }

    /**
     * Parses a pet from a raw line of format: name;date;petType;owner[;operation]\n
     * @param line
     * @return
     * @throws ParseException
     */
    private Pet parsePetFromLine(String line) throws ParseException {
        Pet pet = new Pet();
        String[] fields = line.split(String.valueOf(fieldSeparator));
        pet.setName(fields[0]);
        Date birthDate = parseBirthDate(fields[1]);
        pet.setBirthDate(birthDate);
        PetType petType = parsePetType(fields[2]);
        pet.setType(petType);
        Owner owner = parseOwner(fields[3]);
        pet.setOwner(owner);

        if (fields.length > 4) {
            String operation = fields[4];
            performOperation(operation, pet);
        } else { // no operation provided
            performOperation("add", pet);
        }
        return pet;
    }

    private Date parseBirthDate(String date) throws ParseDateException {
        try {
            return (new SimpleDateFormat(dateFormat)).parse(date);
        } catch (ParseException e) {
            throw new ParseDateException(String.format(ParseDateException.DATE_NOT_VALID_FORMAT, date), 0);
        }
    }

    private PetType parsePetType(String petType) {
        Collection<PetType> allPetTypes = clinicService.findPetTypes();
        for (PetType type : allPetTypes) {
            if (type.getName().equalsIgnoreCase(petType)) {
                return type;
            }
        }
        return null;
    }

    private Owner parseOwner(String owner) throws ParseOwnerException {
        List<Owner> matchingOwners = clinicService.findAllOwners()
            .stream()
            .filter(o -> o.getLastName().equals(owner))
            .collect(Collectors.toList());

        if (matchingOwners.size() == 0) {
            throw new ParseOwnerException(ParseOwnerException.OWNER_NOT_FOUND, 0);
        }
        if (matchingOwners.size() > 1) {
            throw new ParseOwnerException(ParseOwnerException.OWNER_NOT_UNIQUE, 0);
        }
        return matchingOwners.get(0);
    }

    private void performOperation(String operationString, Pet pet) {
        if (operationString.equalsIgnoreCase("add")) {
            clinicService.savePet(pet);
        } else {
            // operation is "delete"
            for (Pet petFromOwner : pet.getOwner().getPets()) {
                if (petFromOwner.equals(pet)) {
                    clinicService.deletePet(petFromOwner);
                }
            }
        }
    }

    private static class ParseOwnerException extends ParseException {
        public static final String OWNER_NOT_FOUND = "Owner not found";
        public static final String OWNER_NOT_UNIQUE = "Owner not unique";

        public ParseOwnerException(String s, int errorOffset) {
            super(s, errorOffset);
        }
    }

    private static class ParseDateException extends ParseException {
        public static final String DATE_NOT_VALID_FORMAT = "date %s not valid";

        public ParseDateException(String s, int errorOffset) {
            super(s, errorOffset);
        }
    }
}
