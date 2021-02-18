package org.springframework.samples.petclinic.rest.importcsv;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectRetrievalFailureException;
import org.springframework.samples.petclinic.model.Owner;
import org.springframework.samples.petclinic.model.Pet;
import org.springframework.samples.petclinic.model.PetType;
import org.springframework.samples.petclinic.service.ClinicService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@RestController
@CrossOrigin(exposedHeaders = "errors, content-type")
@RequestMapping("api/import")
public class ImportCSV {

    @Autowired
    private ClinicService clinicService;

    private static final int COMMAND_FIELD_NUMBER = 5;
    private static final String COMMAND_FORMAT = "name;date(yyyy-MM-dd);type id;owner's id;command";
    private static final String COMMAND_DELIMITER = ";";

    private static final int PET_NAME_INDEX = 0;
    private static final int DATE_INDEX = 1;
    private static final int TYPE_INDEX = 2;
    private static final int OWNER_ID_INDEX = 3;
    private static final int COMMAND_TYPE_INDEX = 4;

    private static final String PET_NAME_REGEX = "^[a-zA-Z]+$";
    private static Pattern namePattern = Pattern.compile(PET_NAME_REGEX);

    private static final String DATE_PATTERN = "yyyy-MM-dd";

    private static Set<String> supportedCommands = new HashSet<>();
    static {
        supportedCommands.add("add");
        supportedCommands.add("delete");
    }

    @PreAuthorize("hasRole(@roles.OWNER_ADMIN)")
    @RequestMapping(value = "importPets",
        method = RequestMethod.POST,
        consumes = "text/plain",
        produces = "application/json")
    public ResponseEntity<List<Pet>> importPets(@RequestBody String csv) {

        List<Pet> response = new ArrayList<>();
        String[] commands = csv.split("\n");

        // execution policy: if a faulty command is discovered, should we execute the rest, execute non, execute only the ones before?
        // create a method to report for each error type

        for(String command: commands){
            String[] fields = parseCommand(command);

            String name = fields[PET_NAME_INDEX];
            String dateString = fields[DATE_INDEX];
            String typeIDString = fields[TYPE_INDEX];
            String ownerIDString = fields[OWNER_ID_INDEX];
            String commandType = fields[COMMAND_TYPE_INDEX].toLowerCase();

            String errorMessage = validateCommand(fields, command);
            if(!errorMessage.isEmpty()){
                return createHttpError(errorMessage, HttpStatus.BAD_REQUEST);
            }

            Date date = parseDate(dateString);

            int ownerID = Integer.parseInt(ownerIDString);
            Owner owner;

            try {
                owner = clinicService.findOwnerById(ownerID);
            }catch (ObjectRetrievalFailureException|EmptyResultDataAccessException e){
                errorMessage = "In command: " + command + "\n";
                errorMessage += "Owner is not found, given this ID: " + ownerID;
                return createHttpError(errorMessage, HttpStatus.NOT_FOUND);
            }

            int typeID = Integer.parseInt(typeIDString);
            PetType type;

            try {
                type = clinicService.findPetTypeById(typeID);
            }catch (ObjectRetrievalFailureException|EmptyResultDataAccessException e){
                errorMessage = "In command: " + command + "\n";
                errorMessage += "Pet type is not found, given this ID: " + typeID;
                return createHttpError(errorMessage, HttpStatus.NOT_FOUND);
            }

            Pet pet = createPet(name, date, type);

            // Beware of setting owner before deletion. This will result in no deletion occurring.
            if(commandType.equals("add")){
                pet.setOwner(owner);
                clinicService.savePet(pet);
            }else{ // already validated command types, and we only have two commands. This should be the delete command.
                List<Pet> pets = owner.getPets();

                for(Pet pet1: pets){
                    if(pet1.equals(pet)){
                        clinicService.deletePet(pet1);
                        break;
                    }
                }
            }

            pet.setOwner(owner); // setting owner here only for the http response
            response.add(pet);
        }

        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    private String[] parseCommand(String command){
        return command.split(COMMAND_DELIMITER);
    }

    private Date parseDate(String dateString){
        Date date = new Date();
        try {
            date = new SimpleDateFormat("yyyy-MM-dd").parse(dateString);
        }catch (ParseException e){
            // already handled in validateDate, should never reach this clause
        }

        return date;
    }

    private String validateCommand(String[] fields, String command){
        String errorMessage = "";

        errorMessage = validateCommandFieldLength(fields.length, command);
        if(!errorMessage.isEmpty()){
            return errorMessage;
        }

        errorMessage = validateName(fields[PET_NAME_INDEX], command);
        if(!errorMessage.isEmpty()){
            return errorMessage;
        }

        errorMessage = validateCommandType(fields[COMMAND_TYPE_INDEX], command);
        if(!errorMessage.isEmpty()){
            return errorMessage;
        }

        errorMessage = validateDate(fields[DATE_INDEX], command);
        if(!errorMessage.isEmpty()){
            return errorMessage;
        }

        errorMessage = validatePetTypeID(fields[TYPE_INDEX], command);
        if(!errorMessage.isEmpty()){
            return errorMessage;
        }

        errorMessage = validateOwnerID(fields[OWNER_ID_INDEX], command);
        if(!errorMessage.isEmpty()){
            return errorMessage;
        }

        return errorMessage;
    }

    private String validateCommandFieldLength(int fieldCount, String command){
        String errorMessage = "";
        if(fieldCount != COMMAND_FIELD_NUMBER){
            errorMessage = "In command: " + command + "\n";
            errorMessage += "The number of fields is: " + fieldCount + ". It should be " + COMMAND_FIELD_NUMBER;
            errorMessage += "Usage: " + COMMAND_FORMAT;
        }

        return errorMessage;
    }

    private String validateName(String name, String command) {
        String errorMessage = "";

        Matcher matcher = namePattern.matcher(name);
        if (!matcher.matches()) {
            errorMessage = "In command: " + command + "\n";
            errorMessage += "Names should only contain letters. Got instead: " + name;
        }
        return errorMessage;
    }

    private String validateCommandType(String commandType, String command) {
        String errorMessage = "";

        if (!supportedCommands.contains(commandType)) {
            errorMessage = "In command: " + command + "\n";
            errorMessage += "Command type: " + commandType + " is unsupported.";
            errorMessage += "Supported command types are: " + supportedCommands.toString();
        }
        return errorMessage;
    }

    private String validateDate(String dateString, String command) {
        String errorMessage = "";

        try{
            Date date = new SimpleDateFormat("yyyy-MM-dd").parse(dateString);
        }catch (ParseException e){
            errorMessage = "In command: " + command + "\n";
            errorMessage += "The date should follow this format: " + DATE_PATTERN + "\n";
            errorMessage += "Instead got: " + dateString;
        }
        return errorMessage;
    }

    private String validatePetTypeID(String typeID, String command) {
        String errorMessage = "";

        if(!validateID(typeID)){
            errorMessage = "In command: " + command + "\n";
            errorMessage += "ID should be a number, instead got: " + typeID;
        }

        return errorMessage;
    }

    private String validateOwnerID(String ownerID, String command) {
        String errorMessage = "";

        if(!validateID(ownerID)){
            errorMessage = "In command: " + command + "\n";
            errorMessage += "ID should be a number, instead got: " + ownerID;
        }

        return errorMessage;
    }

    private boolean validateID(String id){
        try{
            Integer.parseInt(id);
            return true;
        }catch (NumberFormatException e){
            return false;
        }
    }

    private Pet createPet(String name, Date date, PetType type){
        Pet pet = new Pet();
        pet.setName(name);
        pet.setBirthDate(date);
        pet.setType(type);

        return pet;
    }

    private ResponseEntity<List<Pet>> createHttpError(String message, HttpStatus status){
        HttpHeaders headers = new HttpHeaders();
        headers.add("errors", message);
        return new ResponseEntity<>(headers, status);
    }
}
