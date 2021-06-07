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
        List<Pet> pets = new LinkedList<Pet>();

        CsvFieldReader fieldReader = new CsvFieldReader(csv);

        do {
            Pet pet = new Pet();

            String field = fieldReader.nextField();

            pet.setName(field);

            field = fieldReader.nextField();

            try {
                pet.setBirthDate((new SimpleDateFormat("yyyy-MM-dd")).parse(field));
            } catch (ParseException e) {
                HttpHeaders headers = new HttpHeaders();
                headers.add("errors", "date " + field + " not valid");
                return new ResponseEntity<List<Pet>>(headers, HttpStatus.BAD_REQUEST);
            }

            field = fieldReader.nextField();

            ArrayList<PetType> ts = (ArrayList<PetType>) clinicService.findPetTypes();
            for (PetType t : ts) {
                if (t.getName().toLowerCase().equals(field)) {
                    pet.setType(t);
                    break;
                }
            }


            field = fieldReader.nextField();

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


            if (!fieldReader.isEndOfLine()) {
                field = fieldReader.nextField();

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
            pets.add(pet);

        } while (!fieldReader.atEnd());

        return new ResponseEntity<List<Pet>>(pets, HttpStatus.OK);
    }

    private static class CsvFieldReader {
        private int position;
        private final String csv;

        public CsvFieldReader(String csv) {
            this.position = 0;
            this.csv = csv;
        }

        public String nextField() {
            int startIndex = position;
            while (!atEnd() && csv.charAt(position) != ';' && csv.charAt(position) != '\n') {
                ++position;
            }
            String field = csv.substring(startIndex, position);

            // need to go past the ";" and "\n" characters
            if (!atEnd()) {
                ++position;
            }

            return field;
        }

        private boolean atEnd() {
            return position == csv.length();
        }

        public boolean isEndOfLine() {
            if (position == 0) {
                return atEnd();
            } else {
                return csv.charAt(position - 1) == '\n';
            }
        }

        public boolean hasNextField() {
            return !atEnd();
        }
    }

}
