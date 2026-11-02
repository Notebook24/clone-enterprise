package com.orangeandbronze.enlistment.repositories;

import com.orangeandbronze.enlistment.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.stereotype.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

@Repository
public interface SectionRepository extends JpaRepository<Section, String> {
  
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT s FROM Section s WHERE s.sectionId = :sectionId")
  Optional<Section> findByIdForUpdate(@Param("sectionId") String sectionId);
}
