package rw.animalproduct.animal.production.services;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import rw.animalproduct.animal.production.entity.Users;
import rw.animalproduct.animal.production.repository.UsersRepository;

import java.util.ArrayList;
import java.util.List;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UsersRepository usersRepository;

    public CustomUserDetailsService(UsersRepository usersRepository) {
        this.usersRepository = usersRepository;
    }


    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        Users user = usersRepository.findByEmail(email)
                .orElseThrow(() ->new UsernameNotFoundException("User not found with email: " + email));
        if(!user.isActive()){
            throw new UsernameNotFoundException("User account is inactive");
        }
        List<GrantedAuthority> authorities = new ArrayList<>();

        //convert user type to Spring Security role
        String userTypeName = user.getUserTypeId().getUserTypeName();
        String role ="ROLE_" + userTypeName.toUpperCase().replace(" ", "_");
        authorities.add(new SimpleGrantedAuthority(role));
        return new org.springframework.security.core.userdetails.User(
                user.getEmail(),
                user.getPassword(),
                user.isActive(),
                true, // accountNonExpired
                true, // credentialsNonExpired
                true, // accountNonLocked
                authorities
        );
    }
}
