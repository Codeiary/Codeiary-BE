package fixtures.persistence;

import com.codeiary.global.entity.TimeBaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "sample_entry")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SampleEntry extends TimeBaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;

    private String category;

    public SampleEntry(String title, String category) {
        this.title = title;
        this.category = category;
    }

    public void rename(String title) {
        this.title = title;
    }
}
