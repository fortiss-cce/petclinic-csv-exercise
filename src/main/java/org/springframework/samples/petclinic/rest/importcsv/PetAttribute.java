package org.springframework.samples.petclinic.rest.importcsv;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class PetAttribute {

    String name;
    String birthdayString;
    String animal;
    String owner;
    String operation;

    public PetAttribute(String name, String birthday, String animal, String owner, String operation){
        this.name = name;
        this.birthdayString = birthday;
        this.animal = animal;
        this.owner = owner;
        this.operation = operation;
    }

    public boolean isPetAttributeValid(){
        // add more checks here if necessary
        return isOperationValid();
    }

    private boolean isOperationValid(){
        return (this.operation == "add") || (this.operation == "delete");
    }

    public String getName() {
        return name;
    }

    public void setName(final String name) {
        this.name = name;
    }

    public String getBirthday() {
        return birthdayString;
    }


    public Date getBirthdayDateFormat() throws ParseException {
        return (new SimpleDateFormat("yyyy-MM-dd")).parse(this.birthdayString);
    }

    public String getAnimal() {
        return animal;
    }

    public void setAnimal(final String animal) {
        this.animal = animal;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(final String owner) {
        this.owner = owner;
    }

    public String getOperation() {
        return operation;
    }

    public void setOperation(final String operation) {
        this.operation = operation;
    }


}
