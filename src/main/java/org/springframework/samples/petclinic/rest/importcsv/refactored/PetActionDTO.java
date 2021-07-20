package org.springframework.samples.petclinic.rest.importcsv.refactored;

import org.springframework.samples.petclinic.model.Owner;
import org.springframework.samples.petclinic.model.PetType;

import java.util.Date;

class PetActionDTO {
    private String name;
    private PetType type;
    private Date dateOfBirth;
    private Owner owner;
    private Action action;

    enum Action {
        ADD,
        DELETE
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public PetType getType() {
        return type;
    }

    public void setType(PetType type) {
        this.type = type;
    }

    public Date getBirthDate() {
        return dateOfBirth;
    }

    public void setDateOfBirth(Date dateOfBirth) {
        this.dateOfBirth = dateOfBirth;
    }

    public Owner getOwner() {
        return owner;
    }

    public void setOwner(Owner owner) {
        this.owner = owner;
    }

    public Action getAction() {
        return action;
    }

    public void setAction(Action action) {
        this.action = action;
    }
}
