package ru.serega6531.packmate.service;

import com.google.common.primitives.Bytes;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.serega6531.packmate.model.*;
import ru.serega6531.packmate.repository.StreamRepository;

import java.util.*;

@Service
@Slf4j
public class StreamService {

    private final StreamRepository repository;
    private final PatternService patternService;
    private final ServicesService servicesService;
    private final PacketService packetService;
    private final StreamSubscriptionService subscriptionService;

    private final String localIp;
    private final boolean ignoreEmptyPackets;

    private final byte[] GZIP_HEADER = {0x1f, (byte) 0x8b, 0x08};

    @Autowired
    public StreamService(StreamRepository repository,
                         PatternService patternService,
                         ServicesService servicesService,
                         PacketService packetService,
                         StreamSubscriptionService subscriptionService,
                         @Value("${local-ip}") String localIp,
                         @Value("${ignore-empty-packets}") boolean ignoreEmptyPackets) {
        this.repository = repository;
        this.patternService = patternService;
        this.servicesService = servicesService;
        this.packetService = packetService;
        this.subscriptionService = subscriptionService;
        this.localIp = localIp;
        this.ignoreEmptyPackets = ignoreEmptyPackets;
    }

    /**
     * @return был ли сохранен стрим
     */
    @Transactional
    public boolean saveNewStream(UnfinishedStream unfinishedStream, List<Packet> packets) {
        final Optional<CtfService> serviceOptional = servicesService.findService(
                localIp,
                unfinishedStream.getFirstIp().getHostAddress(),
                unfinishedStream.getFirstPort(),
                unfinishedStream.getSecondIp().getHostAddress(),
                unfinishedStream.getSecondPort()
        );

        if (!serviceOptional.isPresent()) {
            log.warn("Не удалось сохранить стрим: сервиса на порту {} или {} не существует",
                    unfinishedStream.getFirstPort(), unfinishedStream.getSecondPort());
            return false;
        }

        Stream stream = new Stream();
        stream.setProtocol(unfinishedStream.getProtocol());
        stream.setStartTimestamp(packets.get(0).getTimestamp());
        stream.setEndTimestamp(packets.get(packets.size() - 1).getTimestamp());
        stream.setService(serviceOptional.get());

        if (ignoreEmptyPackets) {
            packets.removeIf(packet -> packet.getContent().length == 0);

            if (packets.isEmpty()) {
                log.debug("Стрим состоит только из пустых пакетов и не будет сохранен");
                return false;
            }
        }

        boolean gzipStarted = false;
        byte[] gzipContent = null;
        int gzipStartPacket = 0;
        int gzipEndPacket = 0;

        for (int i = 0; i < packets.size(); i++) {
            Packet packet = packets.get(i);

            if (packet.isIncoming() && gzipStarted) {
                gzipStarted = false;
                gzipEndPacket = i - 1;
                //TODO end and read gzip stream
            } else if (!packet.isIncoming()) {
                String content = new String(packet.getContent());

                int contentPos = content.indexOf("\r\n\r\n");
                boolean http = content.startsWith("HTTP/");

                if(http && gzipStarted) {
                    gzipEndPacket = i - 1;
                    //TODO end and read gzip stream
                }

                if (contentPos != -1) {   // начало body
                    String headers = content.substring(0, contentPos);
                    boolean gziped = headers.contains("Content-Encoding: gzip\r\n");
                    if (gziped) {
                        gzipStarted = true;
                        gzipStartPacket = i;
                        int gzipStart = Bytes.indexOf(packet.getContent(), GZIP_HEADER);
                        gzipContent = Arrays.copyOfRange(packet.getContent(), gzipStart, packet.getContent().length);
                    }
                } else if (gzipStarted) {  // продолжение body
                    gzipContent = ArrayUtils.addAll(gzipContent, packet.getContent());
                }
            }
        }

        if(gzipContent != null) {
            gzipEndPacket = packets.size() - 1;
            // TODO end and read gzip stream
        }

        Stream savedStream = save(stream);

        List<ru.serega6531.packmate.model.Packet> savedPackets = new ArrayList<>();
        Set<Pattern> matches = new HashSet<>();

        for (ru.serega6531.packmate.model.Packet packet : packets) {
            packet.setStream(savedStream);
            savedPackets.add(packetService.save(packet));
            matches.addAll(patternService.findMatching(packet.getContent()));
        }

        savedStream.setFoundPatterns(new ArrayList<>(matches));
        savedStream.setPackets(savedPackets);
        savedStream = save(savedStream);

        subscriptionService.broadcastNewStream(savedStream);
        return true;
    }

    public Stream save(Stream stream) {
        Stream saved;
        if (stream.getId() == null) {
            saved = repository.save(stream);
            log.info("Создан стрим с id {}", saved.getId());
        } else {
            saved = repository.save(stream);
        }

        return saved;
    }

    public Optional<Stream> find(long id) {
        return repository.findById(id);
    }

    @Transactional
    public void setFavorite(long id, boolean favorite) {
        final Optional<Stream> streamOptional = repository.findById(id);
        if (streamOptional.isPresent()) {
            final Stream stream = streamOptional.get();
            stream.setFavorite(favorite);
            repository.save(stream);
        }
    }

    public List<Stream> findFavorites(Pagination pagination) {
        PageRequest page = PageRequest.of(0, pagination.getPageSize(), pagination.getDirection(), "id");

        if (pagination.getPattern() != null) { // задан паттерн для поиска
            if (pagination.getDirection() == Sort.Direction.ASC) {  // более новые стримы
                return repository.findAllByIdGreaterThanAndFavoriteIsTrueAndFoundPatternsContaining(pagination.getStartingFrom(), pagination.getPattern(), page);
            } else {  // более старые стримы
                return repository.findAllByIdLessThanAndFavoriteIsTrueAndFoundPatternsContaining(pagination.getStartingFrom(), pagination.getPattern(), page);
            }
        } else {
            if (pagination.getDirection() == Sort.Direction.ASC) {  // более новые стримы
                return repository.findAllByIdGreaterThanAndFavoriteIsTrue(pagination.getStartingFrom(), page);
            } else {  // более старые стримы
                return repository.findAllByIdLessThanAndFavoriteIsTrue(pagination.getStartingFrom(), page);
            }
        }
    }

    public List<Stream> findFavoritesByService(Pagination pagination, CtfService service) {
        PageRequest page = PageRequest.of(0, pagination.getPageSize(), pagination.getDirection(), "id");

        if (pagination.getPattern() != null) { // задан паттерн для поиска
            if (pagination.getDirection() == Sort.Direction.ASC) {  // более новые стримы
                return repository.findAllByServiceAndIdGreaterThanAndFavoriteIsTrueAndFoundPatternsContaining(service, pagination.getStartingFrom(), pagination.getPattern(), page);
            } else {  // более старые стримы
                return repository.findAllByServiceAndIdLessThanAndFavoriteIsTrueAndFoundPatternsContaining(service, pagination.getStartingFrom(), pagination.getPattern(), page);
            }
        } else {
            if (pagination.getDirection() == Sort.Direction.ASC) {  // более новые стримы
                return repository.findAllByServiceAndIdGreaterThanAndFavoriteIsTrue(service, pagination.getStartingFrom(), page);
            } else {  // более старые стримы
                return repository.findAllByServiceAndIdLessThanAndFavoriteIsTrue(service, pagination.getStartingFrom(), page);
            }
        }
    }

    public List<Stream> findAll(Pagination pagination) {
        PageRequest page = PageRequest.of(0, pagination.getPageSize(), pagination.getDirection(), "id");

        if (pagination.getPattern() != null) { // задан паттерн для поиска
            if (pagination.getDirection() == Sort.Direction.ASC) {  // более новые стримы
                return repository.findAllByIdGreaterThanAndFoundPatternsContaining(pagination.getStartingFrom(), pagination.getPattern(), page);
            } else {  // более старые стримы
                return repository.findAllByIdLessThanAndFoundPatternsContaining(pagination.getStartingFrom(), pagination.getPattern(), page);
            }
        } else {
            if (pagination.getDirection() == Sort.Direction.ASC) {  // более новые стримы
                return repository.findAllByIdGreaterThan(pagination.getStartingFrom(), page);
            } else {  // более старые стримы
                return repository.findAllByIdLessThan(pagination.getStartingFrom(), page);
            }
        }
    }

    public List<Stream> findAllByService(Pagination pagination, CtfService service) {
        PageRequest page = PageRequest.of(0, pagination.getPageSize(), pagination.getDirection(), "id");

        if (pagination.getPattern() != null) { // задан паттерн для поиска
            if (pagination.getDirection() == Sort.Direction.ASC) {  // более новые стримы
                return repository.findAllByServiceAndIdGreaterThanAndFoundPatternsContaining(service, pagination.getStartingFrom(), pagination.getPattern(), page);
            } else {  // более старые стримы
                return repository.findAllByServiceAndIdLessThanAndFoundPatternsContaining(service, pagination.getStartingFrom(), pagination.getPattern(), page);
            }
        } else {
            if (pagination.getDirection() == Sort.Direction.ASC) {  // более новые стримы
                return repository.findAllByServiceAndIdGreaterThan(service, pagination.getStartingFrom(), page);
            } else {  // более старые стримы
                return repository.findAllByServiceAndIdLessThan(service, pagination.getStartingFrom(), page);
            }
        }
    }

}
