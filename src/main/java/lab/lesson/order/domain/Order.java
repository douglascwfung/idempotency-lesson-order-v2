package lab.lesson.order.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Integer version;

    @Column(nullable = false)
    private String customerName;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderDetail> lines = new ArrayList<>();

    public void addLine(OrderDetail line) {
        lines.add(line);
        line.setOrder(this);
    }

    public void clearLines() {
        lines.clear();
    }
}
