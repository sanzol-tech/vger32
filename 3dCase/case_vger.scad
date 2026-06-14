// --- PARÁMETROS EDITABLES (Cámbialos aquí) ---
ancho_total = 40;
largo_total = 45;
alto_total = 25;
grosor_pared = 1.6;
tolerancia = 0.2; // Espacio extra para que las piezas encajen

// --- COMPONENTES ---
// 1. Base de la caja
module base() {
    difference() {
        // Cuerpo exterior
        cube([ancho_total, largo_total, alto_total/2], center=true);
        // Vaciado interior
        translate([0,0,grosor_pared])
            cube([ancho_total-grosor_pared*2, largo_total-grosor_pared*2, alto_total/2], center=true);
        
        // Hueco para USB-C del Super Mini
        translate([ancho_total/2, 0, 0])
            cube([grosor_pared+2, 12, 8], center=true);
            
        // Rejillas de ventilación para el BMP180
        for(i = [-1:1]) {
            translate([-ancho_total/2, (largo_total/4) + i*4, 0])
                cube([grosor_pared+2, 2, 10], center=true);
        }
    }
    
    // Soportes internos (Standoffs) para el ESP32
    translate([5, 5, -alto_total/4 + grosor_pared])
        cylinder(h=3, d=3, $fn=20);
    translate([-5, 5, -alto_total/4 + grosor_pared])
        cylinder(h=3, d=3, $fn=20);
}

// 2. Tapa con hueco para PIR
module tapa() {
    translate([0, largo_total + 10, 0]) // Desplazada para verla en el preview
    difference() {
        // Estructura de la tapa
        cube([ancho_total + tolerancia, largo_total + tolerancia, grosor_pared*2], center=true);
        
        // HUECO CIRCULAR PARA EL PIR (Ajusta el radio según tu sensor)
        cylinder(h=grosor_pared*4, r=11.5, center=true, $fn=60); 
    }
}

// Renderizar ambos
base();
tapa();