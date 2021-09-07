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
import java.util.HashMap;
import java.util.Collection;
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

    /**
     * Parses the whole csv file String. The format is each line has to be separated by ';',
     * whereas each line has five elements in the following structure:
     * <p>
     * PET_NAME;PET_BIRTHDAY;PET_TYPE;PET_OWNER;PET_CLINIC_ACTION_COMMAND\n
     * <p>
     * Each line converts to one addition or deletion of an existing pet.
     *
     * @param csv The string which should contain the loaded csv file.
     * @return A matrix in form of an ArrayList<ArrayList<String>>
     */
    static public ArrayList<ArrayList<String>> parseCsvFile(String csv) {
        ArrayList<ArrayList<String>> dataElements = new ArrayList<>();
        ArrayList<String> currentRow = new ArrayList<>();
        dataElements.add(currentRow);
        int oldIndex = 0;
        for (int i = 0; i < csv.length(); ++i) {
            final boolean isEndOfLine = csv.charAt(i) == '\n';
            final boolean isEndOfFile = i + 1 == csv.length();
            final boolean isSeparator = csv.charAt(i) == ';';
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

    /**
     * Converts a single data row from the csv file to a PetRowResult, which contains a Pet and a ClinicPetAction.
     *
     * @param csvRow     The csv row containing the pet_name, pet_birthday, pet_type, pet_owner and pet clinic action
     * @param petTypeMap A mapping of all preexisting pet types to the given pet_type instance in the csvRow
     * @param petOwners  A collection of all existing pet owners
     * @return A PetRowResult containing a new pet and the action to perform in the clinic service
     * @throws Exception if the csvRow does not contain the correct information
     */
    static public PetRowResult parsePetRow(ArrayList<String> csvRow, HashMap<String, PetType> petTypeMap, Collection<Owner> petOwners) throws Exception {
        if (csvRow.size() != 5) {
            throw new Exception("The amount of elements is " + Integer.toString(csvRow.size()) + " in this data row is not five.");
        }
        Pet pet = new Pet();
        // pet name in field 0
        pet.setName(csvRow.get(0));

        // pet birthday in field 1
        final String birthday = csvRow.get(1);
        try {
            pet.setBirthDate((new SimpleDateFormat("yyyy-MM-dd")).parse(birthday));
        } catch (ParseException e) {
            throw new Exception("The birthday data " + birthday + " is not valid!", e);
        }

        // pet type in field 2 ["dog", "cat", ...]
        final String petTypeString = csvRow.get(2);
        PetType petType = petTypeMap.get(petTypeString);
        if (petType != null) {
            pet.setType(petType);
        } else {
            throw new Exception("The pet type " + petTypeString + " was not defined in the clinic pet types.");
        }

        // pet owner in field 3
        final String owner = csvRow.get(3);
        List<Owner> matchingOwners = petOwners.stream().filter(o -> o.getLastName().equals(owner)).collect(Collectors.toList());
        if (matchingOwners.size() == 0) {
            throw new Exception("Owner not found: " + owner);
        } else if (matchingOwners.size() > 1) {
            throw new Exception("Owner not unique: " + owner);
        }
        pet.setOwner(matchingOwners.iterator().next());

        // pet clinic action in field 4 ("add", "delete")
        final String clinicPetAction = csvRow.get(4);
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

    /**
     * Tests if two pets are equal. This should normally be done in the Pet class.
     * This only tests if the name, the type and the birthday are equal.
     *
     * @param pet1 first pet to compare
     * @param pet2 second pet to compare
     * @return boolean if the pets are equal
     */
    public boolean arePetsEqual(Pet pet1, Pet pet2) {
        return pet2.getName().equals(pet1.getName()) &&
            pet2.getType().getId().equals(pet1.getType().getId()) &&
            pet2.getBirthDate().equals(pet1.getBirthDate());
    }

    /**
     * Removes a pet from the clinic service
     *
     * @param pet Pet which should be removed
     */
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

        // map the csv string to a matrix
        ArrayList<ArrayList<String>> csvEntries = ImportCSV.parseCsvFile(csv);

        // create a hash map for all pet type names
        HashMap<String, PetType> petTypeMap = new HashMap<>();
        for (PetType type : clinicService.findPetTypes()) {
            petTypeMap.put(type.getName().toLowerCase(), type);
        }

        Collection<Owner> petOwners = clinicService.findAllOwners();

        // iterate over each row in the csv file
        for (ArrayList<String> csvRow : csvEntries) {
            PetRowResult petRowResult;
            try {
                petRowResult = parsePetRow(csvRow, petTypeMap, petOwners);
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

    /**
     * Creates an error response for a give error message
     *
     * @param errorMsg error message to send back
     * @return A response entity for spring
     */
    static public ResponseEntity<List<Pet>> createErrorResponse(String errorMsg) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("errors", errorMsg);
        return new ResponseEntity<>(headers, HttpStatus.BAD_REQUEST);
    }
}
