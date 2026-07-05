package rw.animalproduct.animal.production.services;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import rw.animalproduct.animal.production.entity.Users;
import rw.animalproduct.animal.production.entity.UsersType;
import rw.animalproduct.animal.production.repository.UsersRepository;
import rw.animalproduct.animal.production.repository.UsersTypeRepository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class UsersService {

    private final UsersRepository usersRepository;
    private final UsersTypeRepository usersTypeRepository;
    private final PasswordEncoder passwordEncoder;

    // Photos are saved to src/main/resources/static/uploads/profiles/
    private static final String UPLOAD_DIR = "src/main/resources/static/uploads/profiles/";

    public UsersService(UsersRepository usersRepository,
                        UsersTypeRepository usersTypeRepository,
                        PasswordEncoder passwordEncoder) {
        this.usersRepository = usersRepository;
        this.usersTypeRepository = usersTypeRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public Users addNew(Users users) {
        users.setActive(true);
        users.setRegistrationDate(new Date());
        users.setPassword(passwordEncoder.encode(users.getPassword()));

        String userTypeIdString = users.getUserTypeIdValue();
        UUID userTypeId = UUID.fromString(userTypeIdString);
        UsersType userType = usersTypeRepository.findById(userTypeId).get();
        users.setUserTypeId(userType);

        return usersRepository.save(users);
    }

    /**
     * Register a new system user with optional photo
     */
    public Users registerUser(String email, String password, String userTypeIdStr, MultipartFile photo) throws IOException {
        Users user = new Users();
        user.setEmail(email);
        user.setActive(true);
        user.setRegistrationDate(new Date());
        user.setPassword(passwordEncoder.encode(password));

        UUID userTypeId = UUID.fromString(userTypeIdStr);
        UsersType userType = usersTypeRepository.findById(userTypeId).get();
        user.setUserTypeId(userType);

        // Save photo if provided
        if (photo != null && !photo.isEmpty()) {
            String photoUrl = savePhoto(photo);
            user.setPhotoUrl(photoUrl);
        }

        return usersRepository.save(user);
    }

    /**
     * Save photo file and return the URL path
     */
    public String savePhoto(MultipartFile photo) throws IOException {
        // Create upload directory if it doesn't exist
        Path uploadPath = Paths.get(UPLOAD_DIR);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        // Generate unique filename to avoid conflicts
        String originalFilename = photo.getOriginalFilename();
        String extension = originalFilename != null && originalFilename.contains(".")
                ? originalFilename.substring(originalFilename.lastIndexOf("."))
                : ".jpg";
        String filename = UUID.randomUUID().toString() + extension;

        // Save the file
        Path filePath = uploadPath.resolve(filename);
        Files.copy(photo.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

        // Return the URL path accessible from browser
        return "/uploads/profiles/" + filename;
    }

    public Optional<Users> getUserByEmail(String email) {
        return usersRepository.findByEmail(email);
    }

    /**
     * Find user by email (alias method for convenience)
     */
    public Optional<Users> findByEmail(String email) {
        return usersRepository.findByEmail(email);
    }

    public List<Users> getAllUsers() {
        return usersRepository.findAll();
    }

    public Optional<Users> getUserById(UUID id) {
        return usersRepository.findById(id);
    }

    public Users updateUser(UUID id, Users updatedUser) {
        Optional<Users> existingUserOpt = usersRepository.findById(id);

        if (existingUserOpt.isPresent()) {
            Users existingUser = existingUserOpt.get();

            if (!existingUser.getEmail().equals(updatedUser.getEmail())) {
                existingUser.setEmail(updatedUser.getEmail());
            }

            if (updatedUser.getPassword() != null && !updatedUser.getPassword().isEmpty()) {
                existingUser.setPassword(passwordEncoder.encode(updatedUser.getPassword()));
            }

            String userTypeIdString = updatedUser.getUserTypeIdValue();
            if (userTypeIdString != null && !userTypeIdString.isEmpty()) {
                UUID userTypeId = UUID.fromString(userTypeIdString);
                UsersType userType = usersTypeRepository.findById(userTypeId).get();
                existingUser.setUserTypeId(userType);
            }

            existingUser.setActive(updatedUser.isActive());
            return usersRepository.save(existingUser);
        }

        return null;
    }

    public void deleteUser(UUID id) {
        usersRepository.deleteById(id);
    }

    public void toggleUserStatus(UUID id) {
        Optional<Users> userOpt = usersRepository.findById(id);
        if (userOpt.isPresent()) {
            Users user = userOpt.get();
            user.setActive(!user.isActive());
            usersRepository.save(user);
        }
    }
}