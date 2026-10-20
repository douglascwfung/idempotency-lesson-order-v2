package lab.lesson.order.web;

import lab.lesson.order.service.OrderService;
import lab.lesson.order.web.dto.CreateOrderRequest;
import lab.lesson.order.web.dto.OrderResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse create(@RequestBody CreateOrderRequest request) {
        return orderService.create(request);
    }

    @GetMapping("/{id}")
    public OrderResponse get(@PathVariable Long id) {
        return orderService.get(id);
    }

    @PutMapping("/{id}")
    public OrderResponse replace(@PathVariable Long id, @RequestBody CreateOrderRequest request) {
        return orderService.replace(id, request);
    }
}
