package rw.animalproduct.animal.production.services;

import org.springframework.stereotype.Service;
import rw.animalproduct.animal.production.entity.UsersType;
import rw.animalproduct.animal.production.repository.UsersTypeRepository;

import java.util.List;

@Service
public class UsersTypeService {
    private final UsersTypeRepository usersTypeRepository;


    public UsersTypeService(UsersTypeRepository usersTypeRepository) {
        this.usersTypeRepository = usersTypeRepository;
    }
        //method to get all

        public List<UsersType> getAll(){
            return usersTypeRepository.findAll();

    }
}
