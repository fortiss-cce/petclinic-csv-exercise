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

    class ParsedEntity {

        private String field;
        private int pos;

        /***
         * contructor
         * @param field
         * @param pos
         */
        public ParsedEntity(String field, int pos) {
            this.field = field;
            this.pos = pos;
        }

        public String getField() {
            return field;
        }

        public void setField(String field) {
            this.field = field;
        }

        public int getPos() {
            return pos;
        }

        public void setPos(int pos) {
            this.pos = pos;
        }
    }

    /***
     * parse the entity
     * @param csv : csv string
     * @param pos : start pared position
     * @return
     */
    public ParsedEntity parseEntity(String csv, int pos) {
        String field = "";

        while (pos < csv.length() && (csv.charAt(pos) != ';' && csv.charAt(pos) != '\n')) {
            field += csv.charAt(pos++);
        }
        return new ParsedEntity(field, pos);
    }

    /***
     * delete the pet record
     * @param pet
     * @return
     */
    public boolean deregisterPetFromOwner(Pet pet) {
        for (Pet q : pet.getOwner().getPets()) {
            if (q.getName().equals(pet.getName())) {
                if (q.getType().getId().equals(pet.getType().getId())) {
                    if (pet.getBirthDate().equals(q.getBirthDate())) {
                        this.clinicService.deletePet(q);
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /***
     * set pet type
     * @param pet
     * @param fieldObj
     * @return
     */
    public Pet setPetType(Pet pet, ParsedEntity fieldObj) {

        ArrayList<PetType> ts = (ArrayList<PetType>) clinicService.findPetTypes();

        for (int j = 0; j < ts.size(); j++) {
            if (ts.get(j).getName().toLowerCase().equals(fieldObj.getField())) {
                pet.setType(ts.get(j));
                break;
            }
        }
        return pet;
    }

    /***
     * get matching owners error
     * @param matchingOwners
     * @return
     */
    public ResponseEntity<List<Pet>> getMatchingOwnerErrorMsg(List<Owner> matchingOwners) {

        HttpHeaders headers = new HttpHeaders();
        if (matchingOwners.size() == 0) {
            headers.add("errors", "Owner not found");
            return new ResponseEntity<List<Pet>>(headers, HttpStatus.BAD_REQUEST);
        } else {
            headers.add("errors", "Owner not unique");
            return new ResponseEntity<List<Pet>>(headers, HttpStatus.BAD_REQUEST);
        }
    }


    @Autowired
    private ClinicService clinicService;

    @PreAuthorize("hasRole(@roles.OWNER_ADMIN)")
    @RequestMapping(value = "importPets",
        method = RequestMethod.POST,
        consumes = "text/plain",
        produces = "application/json")
    public ResponseEntity<List<Pet>> importPets(@RequestBody String csv) {

        int pos = 0;
        List<Pet> pets = new LinkedList<Pet>();
        Pet pet;
        ParsedEntity fieldObj;

        do {
            pet = new Pet();

            fieldObj = this.parseEntity(csv, pos);
            pet.setName(fieldObj.getField());

            pos = fieldObj.getPos() + 1;
            fieldObj = this.parseEntity(csv, pos);

            try {
                pet.setBirthDate((new SimpleDateFormat("yyyy-MM-dd")).parse(fieldObj.getField()));
            } catch (ParseException e) {
                HttpHeaders headers = new HttpHeaders();
                headers.add("errors", "date " + fieldObj.getField() + " not valid");
                return new ResponseEntity<List<Pet>>(headers, HttpStatus.BAD_REQUEST);
            }

            pos = fieldObj.getPos() + 1;
            fieldObj = this.parseEntity(csv, pos);

            //if (pet != null) {
            pet = this.setPetType(pet, fieldObj);
            //}

            pos = fieldObj.getPos() + 1;
            fieldObj = this.parseEntity(csv, pos);

            //if (pet != null) {
            String owner = fieldObj.getField();
            List<Owner> matchingOwners = clinicService.findAllOwners()
                .stream()
                .filter(o -> o.getLastName().equals(owner))
                .collect(Collectors.toList());
            if (matchingOwners.size() == 1) {
                pet.setOwner(matchingOwners.iterator().next());
            } else {
                return this.getMatchingOwnerErrorMsg(matchingOwners);
            }

            //}

            pos = fieldObj.getPos();

            if (csv.charAt(pos) == ';') {
                pos++;

                fieldObj = this.parseEntity(csv, pos);

                if (fieldObj.getField().equalsIgnoreCase("add")) {
                    clinicService.savePet(pet);
                } else {
                    this.deregisterPetFromOwner(pet);
                }

            } else {
                clinicService.savePet(pet);
            }
            pos = fieldObj.getPos() + 1;

            pets.add(pet);

        } while (pos < csv.length() && pet != null);

        return new ResponseEntity<List<Pet>>(pets, HttpStatus.OK);
    }
}
