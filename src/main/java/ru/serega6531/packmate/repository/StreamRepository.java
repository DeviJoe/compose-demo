package ru.serega6531.packmate.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import ru.serega6531.packmate.model.Stream;

import java.util.Optional;

public interface StreamRepository extends JpaRepository<Stream, Long>, JpaSpecificationExecutor<Stream> {

    @Query("UPDATE Stream SET favorite = :favorite WHERE id = :id")
    @Modifying
    void setFavorite(long id, boolean favorite);

    long deleteByEndTimestampBeforeAndFavoriteIsFalse(long threshold);

    @Query("SELECT s FROM Stream s " +
            "JOIN FETCH s.packets AS packets " +
            "LEFT JOIN FETCH packets.matches " +
            "WHERE s.id = :id"
    )
    Optional<Stream> getStreamWithPackets(long id);

}
