package com.springqprobackend.springqpro.repository;

import com.springqprobackend.springqpro.domain.TaskEntity;
import com.springqprobackend.springqpro.enums.TaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

/* NOTES-TO-SELF:
- Spring Data JPA can automatically create repositories (DAOs - Data Access Objects) from interfaces.
- By extending JpaRepository<TaskEntity, String>, you instantly get CRUD methods like:
-- findAll()
-- findById(String id)
-- save(TaskEntity entity)
-- deleteById(String id)
You can also define query methods using naming conventions.
*/
@Repository
public interface TaskRepository extends JpaRepository<TaskEntity, String> {
    // NOTE: ^ In JpaRepository<TaskEntity, String>, the generic parameters mean entity type and primary key type.

    /* Expanding on "You can also define query methods using naming conventions."
    Below is an example of that. W/ these, you never write SQL manually for simple queries.
    -- Method names like findByStatus are descriptive enough to be parsed by Spring to generate queries.
    -- Spring would read the line below as "SLEECT * FROM tasks WHERE status = ?" (REMEMBER THIS!)
    */
    List<TaskEntity> findByStatus(TaskStatus status);
}
