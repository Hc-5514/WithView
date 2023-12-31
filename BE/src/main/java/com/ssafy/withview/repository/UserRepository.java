package com.ssafy.withview.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.ssafy.withview.entity.UserEntity;

@Repository
public interface UserRepository extends JpaRepository<UserEntity, Long> {

	Optional<UserEntity> findBySeq(Long seq);

	Optional<UserEntity> findById(String id);

	Optional<UserEntity> findByIdAndEmail(String id, String email);

	Optional<UserEntity> findByEmail(String email);

	Boolean existsById(String id);

	Boolean existsByEmail(String email);
}