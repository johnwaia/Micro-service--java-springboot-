package com.example.productservice;

import com.example.productservice.model.Product;
import com.example.productservice.repository.ProductRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class DataLoader implements CommandLineRunner {

    private final ProductRepository productRepository;

    public DataLoader(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Override
    public void run(String... args) {
        productRepository.save(new Product("Clavier mecanique", "Clavier mecanique retroeclaire", new BigDecimal("79.90"), 25));
        productRepository.save(new Product("Souris sans fil", "Souris ergonomique sans fil", new BigDecimal("29.90"), 40));
    }
}
