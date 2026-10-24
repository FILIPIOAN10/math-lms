package ro.mathlms.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User,Long> {
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    List<User> findByStatus(AccountStatus status);

    /** The students linked to one parent, by name. */
    List<User> findByParentIdOrderByFullName(Long parentId);

    /**
     * A student ONLY if linked to this parent — the authorization check of the parent API lives in
     * the query, so someone else's child and a non-existent id both come back empty.
     */
    Optional<User> findByIdAndParentId(Long id, Long parentId);

    /**
     * Accounts of a given status and role, with the parent eagerly fetched so the DTO
     * mapping can read it after the transaction closes (open-in-view is off). Ordered
     * by name for a stable admin list.
     */
    @Query("select u from User u left join fetch u.parent"
            + " where u.status = :status and u.role = :role order by u.fullName")
    List<User> findByStatusAndRoleFetchParent(@Param("status") AccountStatus status,
                                              @Param("role") Role role);

    /** Găsește utilizatorii după rol și status care NU au contul șters (erased = false). */
    List<User> findByRoleAndStatusAndErasedFalse(Role role, AccountStatus status);

    /**
     * Unlinks every student of this parent (GDPR erasure of the parent): the children must not stay tied to a tombstone,
     * which would also block linking them to a new parent. Returns how many were unlinked.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update User u set u.parent = null where u.parent.id = :parentId")
    int unlinkChildrenOf(@Param("parentId") Long parentId);
}
