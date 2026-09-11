package com.example.productservice.web;

public class ProductNotFoundException extends RuntimeException {

    public ProductNotFoundException(Long id) {
        super("Produit introuvable avec l'id " + id);
    }
}
