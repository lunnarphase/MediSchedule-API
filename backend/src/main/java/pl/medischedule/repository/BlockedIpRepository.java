package pl.medischedule.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.medischedule.domain.BlockedIp;

public interface BlockedIpRepository extends JpaRepository<BlockedIp, String> {}
