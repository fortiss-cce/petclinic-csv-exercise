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
import java.util.*;
import java.util.stream.Collectors;

@RestController
@CrossOrigin(exposedHeaders = "errors, content-type")
@RequestMapping("api/import")
public class ImportCSV {

    private static final String LINE_SEPARATOR = "\n";
    private static final String COLUMN_SEPARATOR = ";";
    private static final int FIELDS_PER_LINE = 5;

    @Autowired
    private ClinicService clinicService;

    @PreAuthorize("hasRole(@roles.OWNER_ADMIN)")
    @RequestMapping(value = "importPets",
        method = RequestMethod.POST,
        consumes = "text/plain",
        produces = "application/json")
    public ResponseEntity<List<Pet>> importPets(@RequestBody String csv) {

        List<Pet> pets = new LinkedList<Pet>();

        Pet pet;

        List<String> lines = splitLines(csv);

        if (lines.size() == 0) {
            HttpHeaders headers = new HttpHeaders();
            headers.add("File Format", "Empty file or line separator not correct.");
            return new ResponseEntity<List<Pet>>(headers, HttpStatus.BAD_REQUEST);

        }

        for (String line : lines) {
            List<String> fields = parseLine(line);

            if (fields.size() != FIELDS_PER_LINE) {
                // skipping bad lines, logging can  be added
                continue;
            }
            pet = new Pet();

            String nameField = fields.get(0);
            pet.setName(nameField);


            String birthDateField = fields.get(1);

            Date birthDate = new Date();

            try {
                birthDate = parseDate(birthDateField);
            } catch (ParseException e) {
                HttpHeaders headers = new HttpHeaders();
                headers.add("errors", "date " + birthDateField + " not valid");
                return new ResponseEntity<List<Pet>>(headers, HttpStatus.BAD_REQUEST);
            }

            pet.setBirthDate(birthDate);

            String typeField = fields.get(2);

            PetType type = matchPetType(typeField);

            if (type != null) {
                pet.setType(type);
            } else {
                // log here that the type is wrong or does not exist
                HttpHeaders headers = new HttpHeaders();
                headers.add("Errors", "Type " + typeField + " not valid");
                return new ResponseEntity<List<Pet>>(headers, HttpStatus.BAD_REQUEST);
            }

            String ownerField = fields.get(3);
            List<Owner> matchingOwners = findOwnersByLastName(ownerField);

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

            pet.setOwner(matchingOwners.get(0));

            String actionField = fields.get(4);

            if (actionField.toLowerCase().equals("add")) {
                clinicService.savePet(pet);
            } else if (actionField.toLowerCase().equals("delete")) {
                Pet petToDelete = findExactPet(pet);

                if (petToDelete != null) {
                    clinicService.deletePet(petToDelete);
                } else {
                    HttpHeaders headers = new HttpHeaders();
                    headers.add("Errors", "Did not find pet to delete.");
                    return new ResponseEntity<List<Pet>>(headers, HttpStatus.BAD_REQUEST);
                }

            } else {
                HttpHeaders headers = new HttpHeaders();
                headers.add("Errors", "Action invalid, " + actionField + ", use add or delete.");
                return new ResponseEntity<List<Pet>>(headers, HttpStatus.BAD_REQUEST);
            }

            pets.add(pet);

        }
        return new ResponseEntity<List<Pet>>(pets, HttpStatus.OK);
    }

    private List<String> splitLines(String csvFileContent) {
        String[] lines = csvFileContent.split(LINE_SEPARATOR);
        return Arrays.asList(lines);
    }

    private List<String> parseLine(String line) {
        List<String> fields = Arrays.asList(line.split(COLUMN_SEPARATOR));
        fields.forEach(String::strip);
        return fields;
    }

    private Date parseDate(String dateField) throws ParseException {
        Date date;
        try {
            date = (new SimpleDateFormat("yyyy-MM-dd")).parse(dateField);
        } catch (ParseException e) {
            throw e;
        }
        return date;
    }

    private PetType matchPetType(String typeField) {
        PetType type = null;

        ArrayList<PetType> ts = (ArrayList<PetType>) clinicService.findPetTypes();
        for (PetType t : ts) {
            if (t.getName().toLowerCase().equals(typeField)) {
                type = t;
                break;
            }
        }
        return type;
    }

    private List<Owner> findOwnersByLastName(String lastName) {

        return clinicService.findAllOwners()
            .stream()
            .filter(o -> o.getLastName().equals(lastName))
            .collect(Collectors.toList());
    }

    private Pet findExactPet(Pet pet) {
        Pet foundPet = null;
        for (Pet q : pet.getOwner().getPets()) {
            if (q.getName().equals(pet.getName())) {
                if (q.getType().getId().equals(pet.getType().getId())) {
                    if (pet.getBirthDate().equals(q.getBirthDate())) {
                        foundPet = q;
                        break;
                    }
                }
            }
        }
        return foundPet;
    }
}
