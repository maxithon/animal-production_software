package rw.animalproduct.animal.production.services;

import org.springframework.stereotype.Service;
import rw.animalproduct.animal.production.entity.Users;
import rw.animalproduct.animal.production.entity.UsersType;
import rw.animalproduct.animal.production.repository.UsersRepository;
import rw.animalproduct.animal.production.repository.UsersTypeRepository;

import java.util.Date;
import java.util.Optional;
import java.util.UUID;

@Service
public class UsersService {

    private final UsersRepository usersRepository;
    private final UsersTypeRepository usersTypeRepository;

    public UsersService(UsersRepository usersRepository, UsersTypeRepository usersTypeRepository) {
        this.usersRepository = usersRepository;
        this.usersTypeRepository = usersTypeRepository;
    }

    public Users addNew(Users users){
        users.setActive(true);
        users.setRegistrationDate(new Date());

        // Get the userType UUID string from the form
        String userTypeIdString = users.getUserTypeIdValue();

        // Convert string to UUID and find the UserType object
        UUID userTypeId = UUID.fromString(userTypeIdString);
        UsersType userType = usersTypeRepository.findById(userTypeId).get();

        // Set the UserType object (not the string!)
        users.setUserTypeId(userType);
        // Now save
        return usersRepository.save(users);
    }

    public Optional<Users> getUserByEmail(String email){
        return usersRepository.findByEmail(email);
    }


}