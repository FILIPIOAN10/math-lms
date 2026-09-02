package ro.mathlms.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User,Long> {
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    List<User> findByStatus(AccountStatus status);

    /**
     * Accounts of a given status and role, with the parent eagerly fetched so the DTO
     * mapping can read it after the transaction closes (open-in-view is off). Ordered
     * by name for a stable admin list.
     */
    @Query("select u from User u left join fetch u.parent"
            + " where u.status = :status and u.role = :role order by u.fullName")
    List<User> findByStatusAndRoleFetchParent(@Param("status") AccountStatus status,
                                              @Param("role") Role role);
}
