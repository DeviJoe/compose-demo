package ru.serega6531.packmate.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.GenericGenerator;

import javax.persistence.*;
import java.util.Set;

@Data
@Entity
@GenericGenerator(
        name = "packet_generator",
        strategy = "org.hibernate.id.enhanced.SequenceStyleGenerator",
        parameters = {
                @org.hibernate.annotations.Parameter(name = "sequence_name", value = "packet_seq"),
                @org.hibernate.annotations.Parameter(name = "initial_value", value = "1"),
                @org.hibernate.annotations.Parameter(name = "increment_size", value = "1")
        }
)
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(indexes = { @Index(name = "stream_id_index", columnList = "stream_id") })
public class Packet {

    @Id
    @GeneratedValue(generator = "packet_generator")
    private Long id;

    @Transient
    @JsonIgnore
    private Long tempId;

    @Transient
    @JsonIgnore
    private byte ttl;

    @ManyToOne
    @JoinColumn(name = "stream_id", nullable = false)
    @JsonIgnore
    private Stream stream;

    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    private Set<FoundPattern> matches;

    private long timestamp;

    private boolean incoming; // true если от клиента к серверу, иначе false

    private boolean ungzipped;

    private boolean webSocketInflated;

    private byte[] content;

    @Transient
    @JsonIgnore
    public String getContentString() {
        return new String(content);
    }

    public String toString() {
        return "Packet(id=" + id + ", content=" + getContentString() + ")";
    }

}
