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

//removed wildcard import
import java.util.*;

import java.util.stream.Collectors;

/* This class implements the Petclionic Service in the backend.
* The input is a CSV format with semi-colons (;) as separator.
*/

@RestController
@CrossOrigin(exposedHeaders = "errors, content-type")
@RequestMapping("api/import")
public class ImportCSV {

    @Autowired
    private ClinicService clinicService;
    private HttpHeaders headers = new HttpHeaders();

    @PreAuthorize("hasRole(@roles.OWNER_ADMIN)")
    @RequestMapping(value = "importPets",
        method = RequestMethod.POST,
        consumes = "text/plain",
        produces = "application/json")
    public ResponseEntity<List<Pet>> importPets(@RequestBody String csv) {

        /*int i = 0;*/
        List<Pet> pets = new LinkedList<Pet>();
        Pet pet;

        List<String> splitCSV = segment_csv_into_string_list(csv);
        pet = convert_string_list_to_pet( splitCSV );
        // DON'T FORGET TO ADD ERROR HANDLING FOR pet.setOwner:
        // return new ResponseEntity<List<Pet>>(headers, HttpStatus.BAD_REQUEST);

        if (splitCSV.get(4).toLowerCase().equals("add")) {
            clinicService.savePet(pet);
        } else {
            removePetFromOwner(pet);
        }

        pets.add(pet);

        return new ResponseEntity<List<Pet>>(pets, HttpStatus.OK);
    }


    private List<String> segment_csv_into_string_list(String csv)
    {
        List<String> entries;

        entries = Arrays.asList(csv.split(";"));

        return entries;
    }

    private Pet convert_string_list_to_pet( List<String> list )
    {
        Pet pet = new Pet();
        pet.setName(list.get(0));
        pet.setBirthDate(convert_birth_date_from_string(list.get(1)));
        pet.setType(search_for_pet_type_in_list(list.get(2)));

        // ADD EXCEPTION FOR NON-UNIQUE OWNERS !
        List<Owner> matchingOwners = getMatchingOwners(list.get(3));
        if ( isUniqueOwner(matchingOwners) )
        {
            //OLD CODE: pet.setOwner(matchingOwners.iterator().next());
            pet.setOwner(matchingOwners.get(0));
        }

        return pet;
    }

    private Date convert_birth_date_from_string(String birth_date_string)
    {
        Date date = new Date();

        SimpleDateFormat simple_date_format = new SimpleDateFormat("yyyy-MM-dd");

        try {
            date = simple_date_format.parse(birth_date_string);
        }
        catch (ParseException e) {
            HttpHeaders headers = new HttpHeaders();
            headers.add("errors", "date " + birth_date_string + " not valid");

        }

        return date;

    }

    private ArrayList<PetType> get_pet_types_from_server()
    {
        ArrayList<PetType> pet_type_list = (ArrayList<PetType>) clinicService.findPetTypes();
        return pet_type_list;
    }

    private PetType search_for_pet_type_in_list(String entry)
    {
        PetType pet_type = new PetType();

        ArrayList<PetType> pet_type_list = get_pet_types_from_server();

        for (int petIndex = 0; petIndex < pet_type_list.size(); petIndex++)
        {
            if (pet_type_list.get(petIndex).getName().toLowerCase().equals(entry))
            {
                pet_type= pet_type_list.get(petIndex);
                break;
            }
        }

        return pet_type;
    }

    private boolean isUniqueOwner(List<Owner> matchingOwners)
    {
        if (matchingOwners.size() == 1) {
          return true;
        } else if (matchingOwners.size() == 0) {
            HttpHeaders headers = new HttpHeaders();
            headers.add("errors", "Owner not found");
            return false;
        } else { //if (matchingOwners.size() > 1)
            HttpHeaders headers = new HttpHeaders();
            headers.add("errors", "Owner not unique");
            return false;
        }
    }

    private List<Owner> getMatchingOwners(String entry)
    {
      List<Owner> matchingOwners = clinicService.findAllOwners()
          .stream()
          .filter(o -> o.getLastName().equals(entry))
          .collect(Collectors.toList());
      return matchingOwners;
    }

    private boolean isSamePet(Pet pet1, Pet pet2)
    {
        if (pet1.getName().equals(pet2.getName())) {
            if (pet1.getType().getId().equals(pet2.getType().getId())) {
                if (pet2.getBirthDate().equals(pet1.getBirthDate())) {
                    return true;
                }
            }
        }
        return false;
    }

    private void removePetFromOwner(Pet pet)
    {
        for (Pet petFromOwnersList : pet.getOwner().getPets()) {
            if (isSamePet(petFromOwnersList, pet)) {
                clinicService.deletePet(petFromOwnersList);
            }

        }
    }

}
