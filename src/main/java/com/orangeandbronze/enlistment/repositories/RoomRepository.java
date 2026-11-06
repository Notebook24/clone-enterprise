package com.orangeandbronze.enlistment.repositories;

import com.orangeandbronze.enlistment.domain.Room;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RoomRepository extends JpaRepository<Room, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Room r WHERE r.name = :name")
    Optional<Room> findByIdForUpdate(@Param("name") String name);
}
