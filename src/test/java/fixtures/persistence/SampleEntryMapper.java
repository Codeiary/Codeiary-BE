package fixtures.persistence;

import com.codeiary.global.config.MapStructConfig;
import org.mapstruct.Mapper;

@Mapper(config = MapStructConfig.class)
public interface SampleEntryMapper {

    SampleEntryResponse toResponse(SampleEntry entry);
}
