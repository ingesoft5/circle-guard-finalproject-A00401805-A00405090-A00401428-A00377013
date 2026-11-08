/**
 * CG-DOC: persistence repository for the form service: Question Repository.
 */
package com.circleguard.form.repository;

import com.circleguard.form.model.Question;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface QuestionRepository extends JpaRepository<Question, UUID> {
}
