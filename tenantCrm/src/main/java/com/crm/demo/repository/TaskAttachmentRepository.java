package com.crm.demo.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.crm.demo.model.TaskAttachment;
import java.util.List;

@Repository
public interface TaskAttachmentRepository extends JpaRepository<TaskAttachment, Long> {
    List<TaskAttachment> findByTaskId(Long taskId);
    List<TaskAttachment> findByTaskIdAndUploadedBy(Long taskId, String uploadedBy);
}
