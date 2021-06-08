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
import java.util.Date;
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

        int i = 0;
        List<Pet> pets = new LinkedList<Pet>();
        Pet pet;
        CSVContainer csvContainer = new CSVContainer(csv);

        for (int lineIndex = 0; lineIndex < csvContainer.getNumberOfLines(); ++lineIndex) {
            pet = new Pet();

            String[] csvLine = csvContainer.getElementsFromLine(lineIndex);

            pet.setName(csvLine[0]);

            try {
                Date processedBirthDate = parseBirthdate(csvLine[1]);
                pet.setBirthDate(processedBirthDate);
                
                PetType parsedPetType = parsePetType(csvLine[2]);
                pet.setType(parsedPetType);

                Owner parsedOwner = parseOwner(csvLine[3]);
                pet.setOwner(parsedOwner);

                if (!csvLine[4].toLowerCase().equals("add")) {
                    for (Pet q : pet.getOwner().getPets()) {
                        if (q.getName().equals(pet.getName())) {
                            if (q.getType().getId().equals(pet.getType().getId())) {
                                if (pet.getBirthDate().equals(q.getBirthDate())) {
                                    clinicService.deletePet(q);
                                }
                            }
                        }
                    }
                } else {
                    clinicService.savePet(pet);
                }

                pets.add(pet);
            }
            catch (ParseException e) {
                return createErrorResponse("date " + csvLine[1] + " not valid");
            }
            catch(WrongPetTypeException e) {
                return createErrorResponse("pet type " + csvLine[2] + " not valid");
            }
            catch(NotUniqueOwnerException e) {
                return createErrorResponse("Owner not unique");
            }
            catch(OwnerNotFoundException e) {
                return createErrorResponse("Owner not found");
            }
        }

        return new ResponseEntity<List<Pet>>(pets, HttpStatus.OK);
    }


    private Date parseBirthdate(String rawDate) throws ParseException {
        return (new SimpleDateFormat("yyyy-MM-dd")).parse(rawDate);
    }


    private PetType parsePetType(String petType) throws WrongPetTypeException{
        ArrayList<PetType> ts = (ArrayList<PetType>) clinicService.findPetTypes();
        for (int j = 0; j < ts.size(); j++) {
            if (ts.get(j).getName().toLowerCase().equals(petType)) {
                return ts.get(j);
            }
        }
        throw new WrongPetTypeException();
    }


    private Owner parseOwner(String owner) throws OwnerNotFoundException, NotUniqueOwnerException{
        List<Owner> matchingOwners = clinicService.findAllOwners()
                    .stream()
                    .filter(o -> o.getLastName().equals(owner))
                    .collect(Collectors.toList());

        if (matchingOwners.size() == 0) {
            throw new OwnerNotFoundException();
            
        }
        if (matchingOwners.size() > 1) {
            throw new NotUniqueOwnerException();

        }        
        return matchingOwners.iterator().next();
    }
    

    private ResponseEntity<List<Pet>> createErrorResponse(String response){
        HttpHeaders headers = new HttpHeaders();
        headers.add("errors", response);
        return new ResponseEntity<List<Pet>>(headers, HttpStatus.BAD_REQUEST);
    }


    private class CSVContainer{
        public String [] lines;

        public CSVContainer(String csv){
            lines = csv.split("\n");
        }

        public int getNumberOfLines(){
            return lines.length;
        }
        
        public String[] getElementsFromLine(int lineIndex) {
            return lineIndex >= 0 && lineIndex < lines.length ? lines[lineIndex].split(";") : null;
        }
    }


    private class WrongPetTypeException extends Exception {}
    private class NotUniqueOwnerException extends Exception {}
    private class OwnerNotFoundException extends Exception {}
}
