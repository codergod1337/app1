package codergod1337.app1.file.fileextension.repository;

import codergod1337.app1.file.fileextension.model.FileExtension;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FileExtensionRepository extends JpaRepository<FileExtension, String> {

	List<FileExtension> findByFileExtensionCollectionKey(String fileExtensionCollectionKey);

}
