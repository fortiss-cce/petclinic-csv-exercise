package org.springframework.samples.petclinic.rest.importcsv.refactored;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.samples.petclinic.model.Owner;
import org.springframework.samples.petclinic.model.Pet;
import org.springframework.samples.petclinic.model.PetType;
import org.springframework.samples.petclinic.service.ClinicService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

// Example input:
// (petName; dob; petType; ownerLastName; add | delete)
// ```
//   fifi;2012-02-21;dog;Franklin;add
//   rex;2012-02-21;dog;Franklin;add
//   lassie;2012-02-21;dog;Franklin;delete
// ```
@RestController
@CrossOrigin(exposedHeaders = "errors, content-type")
@RequestMapping("api/import3")
public class ImportCSVRefactored {

    @Autowired
    private ClinicService clinicService;

    @PreAuthorize("hasRole(@roles.OWNER_ADMIN)")
    @RequestMapping(value = "pets",
        method = RequestMethod.POST,
        consumes = "text/plain",
        produces = "application/json")
    public ResponseEntity<List<PetActionDTO>> pets(@RequestBody String csv) {
        try {
            List<PetActionDTO> parsedPetActionDTOS = this.parsePetActions(csv);
            return new ResponseEntity<>(parsedPetActionDTOS, HttpStatus.OK);
        } catch (MalformedCSVException e) {
            HttpHeaders headers = new HttpHeaders();
            headers.add("errors", e.getMessage());
            return new ResponseEntity<>(headers, HttpStatus.BAD_REQUEST);
        }
    }

    private List<PetActionDTO> parsePetActions(String csv) throws MalformedCSVException {
        SimpleCSVParser csvParser = new SimpleCSVParser(csv);
        List<PetActionDTO> parsedPetActionDTOS = new ArrayList<>();

        while (csvParser.hasNextRecord()) {
            PetActionDTO petActionDTO = readPetAction(csvParser);
            parsedPetActionDTOS.add(petActionDTO);
            applyAction(petActionDTO);
        }

        return parsedPetActionDTOS;
    }

    private PetActionDTO readPetAction(SimpleCSVParser csvParser) throws MalformedCSVException {
        PetActionDTO petActionDTO = new PetActionDTO();
        List<String> record = csvParser.nextRecord();
        Iterator<String> recordIterator = record.iterator();
        petActionDTO.setName(readName(recordIterator));
        petActionDTO.setDateOfBirth(readDateOfBirth(recordIterator));
        petActionDTO.setType(readType(recordIterator));
        petActionDTO.setOwner(readOwner(recordIterator));
        petActionDTO.setAction(readOptionalAction(recordIterator));
        return petActionDTO;
    }

    private String readName(Iterator<String> csvRecord) throws MalformedCSVException {
        if (!csvRecord.hasNext()) {
            throw new MalformedCSVException("Pet name missing");
        }
        return csvRecord.next();
    }

    private Date readDateOfBirth(Iterator<String> csvRecord) throws MalformedCSVException {
        if (!csvRecord.hasNext()) {
            throw new MalformedCSVException("Date of birth missing");
        }
        String dateEntry = csvRecord.next();
        try {
            return new SimpleDateFormat("yyyy-MM-dd").parse(dateEntry);
        } catch (ParseException e) {
            throw new MalformedCSVException("Date of birth format invalid");
        }
    }

    private PetType readType(Iterator<String> csvRecord) throws MalformedCSVException {
        if (!csvRecord.hasNext()) {
            throw new MalformedCSVException("Pet type missing");
        }
        String typeString = csvRecord.next();
        for (PetType type : clinicService.findAllPetTypes()) {
            if (typeString.equals(type.getName().toLowerCase())) {
                return type;
            }
        }
        throw new MalformedCSVException("Unknown pet type '" + typeString + "'");
    }

    private Owner readOwner(Iterator<String> csvRecord) throws MalformedCSVException {
        if (!csvRecord.hasNext()) {
            throw new MalformedCSVException("Owner name missing");
        }
        String ownerLastNameEntry = csvRecord.next();
        Collection<Owner> owners = clinicService.findOwnerByLastName(ownerLastNameEntry);
        if (owners.isEmpty()) {
            throw new MalformedCSVException("Unknown owner");
        } else if (owners.size() > 1) {
            throw new MalformedCSVException("Owner name not unique");
        } else {
            return owners.iterator().next();
        }
    }


    private PetActionDTO.Action readOptionalAction(Iterator<String> csvRecord) throws MalformedCSVException {
        if (!csvRecord.hasNext()) {
            return PetActionDTO.Action.ADD;
        }
        String actionEntry = csvRecord.next();
        for (PetActionDTO.Action action : PetActionDTO.Action.values()) {
            if (action.toString().equalsIgnoreCase(actionEntry)) {
                return action;
            }
        }
        throw new MalformedCSVException("Unknown action");
    }


    private void applyAction(PetActionDTO petActionDTO) {
        switch (petActionDTO.getAction()) {
            case ADD:
                clinicService.savePet(petActionToPet(petActionDTO));
                break;
            case DELETE:
                for (Pet existingPet : petActionDTO.getOwner().getPets()) {
                    if (Objects.equals(existingPet.getName(), petActionDTO.getName()) &&
                        Objects.equals(existingPet.getType(), petActionDTO.getType()) &&
                        Objects.equals(existingPet.getBirthDate(), petActionDTO.getBirthDate())) {
                        clinicService.deletePet(existingPet);
                    }
                }
                break;
        }
    }

    Pet petActionToPet(PetActionDTO petActionDTO) {
        Pet pet = new Pet();
        pet.setName(petActionDTO.getName());
        pet.setType(petActionDTO.getType());
        pet.setBirthDate(petActionDTO.getBirthDate());
        pet.setOwner(petActionDTO.getOwner());
        return pet;
    }

    private static final class MalformedCSVException extends Exception {
        public MalformedCSVException(String message) {
            super(message);
        }
    }

}
