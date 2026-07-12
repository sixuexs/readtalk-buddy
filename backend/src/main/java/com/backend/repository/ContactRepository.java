package com.backend.repository;

import com.backend.document.ContactDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ContactRepository extends MongoRepository<ContactDocument, String> {

    List<ContactDocument> findAllByOrderByCreatedAtDesc();

    List<ContactDocument> findByRelationType(String relationType);

    /** 查找亲密度低于阈值的联系人 */
    List<ContactDocument> findByIntimacyLessThan(int threshold);

    /** 查找长时间未联系的联系人 */
    List<ContactDocument> findByLastContactDaysGreaterThan(int days);
}
