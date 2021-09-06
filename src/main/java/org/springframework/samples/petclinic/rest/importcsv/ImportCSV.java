package org.springframework.samples.petclinic.rest.importcsv;

import jdk.internal.net.http.common.Pair;
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

    public enum ClinicPetAction {
        ADD, DELETE
    }

    public static class PetRowResult {
        public PetRowResult(Pet pet, ClinicPetAction action) {
            this.pet = pet;
            this.action = action;
        }

        public final Pet pet;
        public final ClinicPetAction action;
    }


    @Autowired
    private ClinicService clinicService;

    static public ArrayList<ArrayList<String>> readNextFieldCSV(String csv) {
        ArrayList<ArrayList<String>> dataElements = new ArrayList<>();
        ArrayList<String> currentRow = new ArrayList<>();
        dataElements.add(currentRow);
        int oldIndex = 0;
        for (int i = 0; i < csv.length(); ++i) {
            boolean isEndOfLine = csv.charAt(i) == '\n';
            boolean isEndOfFile = i + 1 == csv.length();
            boolean isSeparator = csv.charAt(i) == ';';
            if (isSeparator || isEndOfLine) {
                currentRow.add(csv.substring(oldIndex, i));
                oldIndex = i + 1;
            } else if (isEndOfFile) {
                currentRow.add(csv.substring(oldIndex, i + 1));
            }
            if (isEndOfFile || isEndOfLine) {
                dataElements.add(currentRow);
                currentRow = new ArrayList<>();
            }
        }
        return dataElements;
    }

    static public PetRowResult parsePetRow(ArrayList<String> dataRow, HashMap<String, PetType> petTypeMap, Collection<Owner> petOwners) throws Exception {
        if (dataRow.size() != 5) {
            throw new Exception("The amount of elements is " + Integer.toString(dataRow.size()) + " in this data row is not five.");
        }
        Pet pet = new Pet();
        pet.setName(dataRow.get(0));
        String birthday = dataRow.get(1);
        try {
            pet.setBirthDate((new SimpleDateFormat("yyyy-MM-dd")).parse(birthday));
        } catch (ParseException e) {
            throw new Exception("The birthday data " + birthday + " is not valid!", e);
        }

        String petTypeString = dataRow.get(2);
        PetType petType = petTypeMap.get(petTypeString);
        if (petType != null) {
            pet.setType(petType);
        } else {
            throw new Exception("The pet type " + petTypeString + " was not defined in the clinic pet types.");
        }

        String owner = dataRow.get(3);
        List<Owner> matchingOwners = petOwners.stream().filter(o -> o.getLastName().equals(owner)).collect(Collectors.toList());
        if (matchingOwners.size() == 0) {
            throw new Exception("Owner not found: " + owner);
        } else if (matchingOwners.size() > 1) {
            throw new Exception("Owner not unique: " + owner);
        }
        pet.setOwner(matchingOwners.iterator().next());

        String clinicPetAction = dataRow.get(4);
        switch (clinicPetAction.toLowerCase()) {
            case "add":
            case "":
                return new PetRowResult(pet, ClinicPetAction.ADD);
            case "delete":
                return new PetRowResult(pet, ClinicPetAction.DELETE);
            default:
                throw new Exception("Unknown pet action: " + clinicPetAction);
        }
    }

    public boolean arePetsEqual(Pet pet1, Pet pet2) {
        return pet2.getName().equals(pet1.getName()) &&
            pet2.getType().getId().equals(pet1.getType().getId()) &&
            pet2.getBirthDate().equals(pet1.getBirthDate());
    }

    public void removePetFromClinicService(Pet pet) {
        for (Pet sameOwnerPet : pet.getOwner().getPets()) {
            if (arePetsEqual(pet, sameOwnerPet)) {
                clinicService.deletePet(sameOwnerPet);
            }
        }
    }

    @PreAuthorize("hasRole(@roles.OWNER_ADMIN)")
    @RequestMapping(value = "importPets",
        method = RequestMethod.POST,
        consumes = "text/plain",
        produces = "application/json")
    public ResponseEntity<List<Pet>> importPets(@RequestBody String csv) {
        List<Pet> pets = new LinkedList<Pet>();

        ArrayList<ArrayList<String>> dataElements = ImportCSV.readNextFieldCSV(csv);

        HashMap<String, PetType> petTypeMap = new HashMap<>();
        for (PetType type : clinicService.findPetTypes()) {
            petTypeMap.put(type.getName().toLowerCase(), type);
        }

        Collection<Owner> petOwners = clinicService.findAllOwners();

        for (ArrayList<String> dataRow : dataElements) {
            PetRowResult petRowResult;
            try {
                petRowResult = parsePetRow(dataRow, petTypeMap, petOwners);
            } catch (Exception e) {
                return createErrorResponse(e.getMessage());
            }
            switch (petRowResult.action) {
                case ADD:
                    clinicService.savePet(petRowResult.pet);
                    break;
                case DELETE:
                    removePetFromClinicService(petRowResult.pet);
                    break;
                default:
                    return createErrorResponse("Unexpected value: " + petRowResult.action);
            }
            pets.add(petRowResult.pet);
        }
        return new ResponseEntity<List<Pet>>(pets, HttpStatus.OK);

    }

    static public ResponseEntity<List<Pet>> createErrorResponse(String errorMsg){
        HttpHeaders headers = new HttpHeaders();
        headers.add("errors", errorMsg);
        return new ResponseEntity<>(headers, HttpStatus.BAD_REQUEST);
    }
}
