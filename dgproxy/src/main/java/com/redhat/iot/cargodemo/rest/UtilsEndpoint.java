/*
 * ******************************************************************************
 * Copyright (c) 2017 Red Hat, Inc. and others
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *
 * ******************************************************************************
 */
package com.redhat.iot.cargodemo.rest;

import com.redhat.iot.cargodemo.model.*;
import com.redhat.iot.cargodemo.service.DGService;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.*;
import java.util.*;

/**
 * A simple REST service which proxies requests to a local datagrid.
 */

@Path("/utils")
@Singleton
public class UtilsEndpoint {

    public static final int MAX_VEHICLES = 10;
    public static final int MAX_PACKAGES_PER_VEHICLE = 20;
    public static final long DAY_IN_MS = 24*60*60*1000;

    @Inject
    DGService dgService;

    @GET
    @Path("/health")
    public String health() {
        return "ok";
    }

    @POST
    @Path("/resetAll")
    public void resetAll() {

        Map<String, Vehicle> vehiclesCache = dgService.getVehicles();
        Map<String, Customer> customerCache = dgService.getCustomers();
        Map<String, Facility> facilitiesCache = dgService.getFacilities();
        Map<String, Operator> operatorCache = dgService.getOperators();
        Map<String, Shipment> shipmentCache = dgService.getShipments();


        facilitiesCache.clear();
        vehiclesCache.clear();
        customerCache.clear();
        operatorCache.clear();
        shipmentCache.clear();

        for (String COMPANY : COMPANIES) {
            customerCache.put(COMPANY,
                    new Customer(COMPANY, "password"));
        }

        for (String oper : OPERATOR_NAMES){
            operatorCache.put(oper, new Operator(oper, "password"));
        }

        List<String> facs = new ArrayList<String>();
        facs.addAll(Arrays.asList(ORIGINS));
        facs.addAll(Arrays.asList(DESTS));

        for (String facName : facs) {

            facilitiesCache.put(facName,
                    new Facility(facName, facName,
                            new LatLng(-80, 20),
                            Math.random() * 1000.0));

        }


        for (int i = 1; i <= MAX_VEHICLES; i++) {

            String vin = "truck-" + i;

            Vehicle newVehicle = new Vehicle(vin, rand(VEHICLE_TYPES));

            Facility v_origin = facilitiesCache.get(rand(ORIGINS));
            Facility v_dest = facilitiesCache.get(rand(DESTS));

            newVehicle.setOrigin(v_origin);
            newVehicle.setDestination(v_dest);

            List<Telemetry> vehicleTelemetry = new ArrayList<>();
            vehicleTelemetry.add(new Telemetry("°C", 250.0, 150.0, "Temperatura motor", "temp"));
            vehicleTelemetry.add(new Telemetry("rpm", 2200.0, 500.0, "RPM", "rpm"));
            vehicleTelemetry.add(new Telemetry("psi", 80.0, 30.0, "Aceite", "oilpress"));
            newVehicle.setTelemetry(vehicleTelemetry);

            Date v_eta = new Date(new Date().getTime() + DAY_IN_MS + (long)(Math.random() * DAY_IN_MS * 2));

            newVehicle.setEta(v_eta);
            vehiclesCache.put(vin, newVehicle);

            for (int j = 1; j <= MAX_PACKAGES_PER_VEHICLE; j++) {

                addShipment(customerCache, facilitiesCache, shipmentCache, vin, newVehicle, v_dest, "pkg-" + j,
                        rand(PKG_DESCS));
            }

            // Add additional sensor ids if present
            String addl = System.getenv("ADDITIONAL_SENSOR_IDS");
            if (addl != null) {
                String[] ids = addl.split(",");
                for (String id: ids) {
                    addShipment(customerCache, facilitiesCache, shipmentCache, vin, newVehicle, v_dest, id.trim(),
                            rand(PKG_DESCS) + " [" + id.trim() + "]");
                }
            }

        }
        calcUtilization();

    }

    private void addShipment(Map<String, Customer> customerCache, Map<String, Facility> facilitiesCache,
                             Map<String, Shipment> shipmentCache, String vin, Vehicle v, Facility v_dest, String sensor_id,
                             String pkgDesc) {
        List<Facility> route = new ArrayList<Facility>();

        Facility p_origin = facilitiesCache.get(rand(ORIGINS));
        Facility p_dest = facilitiesCache.get(rand(DESTS));

        route.add(p_origin);
        route.add(v_dest);
        route.add(p_dest);

        List<Telemetry> telemetry = new ArrayList<>();
        telemetry.add(new Telemetry("°C", 40.0, 0.0, "Temperatura", "Ambiente"));
        telemetry.add(new Telemetry("%", 100.0, 0.0, "Humedad", "Humedad"));
        telemetry.add(new Telemetry("lm", 400.0, 100.0, "Luz", "Luz"));
        telemetry.add(new Telemetry("inHg", 31, 29, "Presión", "Presión"));

        Customer cust = customerCache.get(rand(COMPANIES));

        // left ~3 days, ago eta ~5 days from now
        Date etd = new Date(new Date().getTime() - DAY_IN_MS - (long)(Math.random() * DAY_IN_MS * 3));
        Date eta = new Date(new Date().getTime() + DAY_IN_MS + (long)(Math.random() * DAY_IN_MS * 4));

        Shipment s = new Shipment(customerCache.get(rand(COMPANIES)),
                "Paquete " + sensor_id + "/" + vin, pkgDesc,
                sensor_id, route, etd, eta, Math.random() * 2000, v);

        s.setTelemetry(telemetry);
        shipmentCache.put(sensor_id + "/" + vin, s);
    }

    private String rand(String[] strs) {
        return strs[(int)Math.floor(Math.random() * strs.length)];
    }

    private void calcUtilization() {
        Map<String, Facility> facCache = dgService.getFacilities();
        Map<String, Shipment> shipCache = dgService.getShipments();

        Map<String, Integer> facCount = new HashMap<>();

        int total = 0;

        for (String s1 : shipCache.keySet()) {
            Shipment s = shipCache.get(s1);
            for (Facility f : s.getRoute()) {
                total++;
                if (facCount.containsKey(f.getName())) {
                    facCount.put(f.getName(), facCount.get(f.getName()) + 1);
                } else {
                    facCount.put(f.getName(), 1);
                }
            }
        }

        for (String s1 : facCache.keySet()) {
            Facility f = facCache.get(s1);
            if (!facCount.containsKey(f.getName())) {
                f.setUtilization(0);
            } else {
                f.setUtilization(2.5 * ((double) facCount.get(f.getName()) / (double) total));
            }
            facCache.put(f.getName(), f);
        }
    }

    @PUT
    @Path("/{id}")
    public void put(@PathParam("id") String id, Vehicle value) {
        dgService.getVehicles().put(id, value);
    }

    @GET
    @Path("/summaries")
    @Produces({"application/json"})
    public List<Summary> getSummaries() {

        List<Summary> result = new ArrayList<>();

        Summary vehicleSummary = getVehicleSummary();
        Summary clientSummary = getClientSUmmary();
        Summary packageSummary = getPackageSummary();
        Summary facilitySummary = getFacilitySummary();
        Summary operatorSummary = getOperatorSummary();

        result.add(clientSummary);
        result.add(packageSummary);
        result.add(vehicleSummary);
        result.add(operatorSummary);
        result.add(facilitySummary);

        Summary mgrs = new Summary();
        mgrs.setName("falso");
        mgrs.setTitle("Directores");
        mgrs.setCount(23);
        mgrs.setWarningCount(4);
        mgrs.setErrorCount(1);
        result.add(mgrs);
        return result;
    }

    private Summary getOperatorSummary() {
        Map<String, Operator> cache = dgService.getOperators();

        Summary summary = new Summary();
        summary.setName("operarios");
        summary.setTitle("Operarios");
        summary.setCount(cache.keySet().size());

        return summary;

    }

    private Summary getFacilitySummary() {
        Map<String, Facility> cache = dgService.getFacilities();

        Summary summary = new Summary();
        summary.setName("plantas");
        summary.setTitle("Plantas");
        summary.setCount(cache.keySet().size());

        long warningCount = cache.keySet().stream()
                .map(cache::get)
                .filter(v -> v.getUtilization() < .7 && v.getUtilization() > .5)
                .count();

        long errorCount = cache.keySet().stream()
                .map(cache::get)
                .filter(v -> v.getUtilization() < .5)
                .count();

        summary.setWarningCount(warningCount);
        summary.setErrorCount(errorCount);

        return summary;
    }

    private Summary getPackageSummary() {
        Map<String, Shipment> cache = dgService.getShipments();

        Summary summary = new Summary();
        summary.setName("paquetes");
        summary.setTitle("Paquetes");
        summary.setCount(cache.keySet().size());


        long warningCount = cache.keySet().stream()
                .map(cache::get)
                .filter(v -> "warning".equalsIgnoreCase(v.getStatus()))
                .count();

        long errorCount = cache.keySet().stream()
                .map(cache::get)
                .filter(v -> "error".equalsIgnoreCase(v.getStatus()))
                .count();

        summary.setWarningCount(warningCount);
        summary.setErrorCount(errorCount);
        return summary;

    }

    private Summary getClientSUmmary() {
        Map<String, Customer> cache = dgService.getCustomers();

        Summary summary = new Summary();
        summary.setName("clientes");
        summary.setTitle("Clientes");
        summary.setCount(cache.keySet().size());
        return summary;

    }

    private Summary getVehicleSummary() {
        Map<String, Vehicle> cache = dgService.getVehicles();

        Summary summary = new Summary();
        summary.setName("vehículos");
        summary.setTitle("Vehículos");
        summary.setCount(cache.keySet().size());


        long warningCount = cache.keySet().stream()
                .map(cache::get)
                .filter(v -> "warning".equalsIgnoreCase(v.getStatus()))
                .count();

        long errorCount = cache.keySet().stream()
                .map(cache::get)
                .filter(v -> "error".equalsIgnoreCase(v.getStatus()))
                .count();

        summary.setWarningCount(warningCount);
        summary.setErrorCount(errorCount);
        return summary;
    }


    public static final String[] COMPANIES = new String[]{
            "Azurmendi",
            "Arzak",
            "Akelarre",
            "DiverXO",
            "Sant Pau",
            "El Celler de Can Roca",
            "Lasarte",
            "ABaC",
            "Kabuki Raw",
            "Subway",
            "KFC",
            "Pans & Company",
            "McDonald's",
            "Five Guys",
            "Domino's Pizza",
            "TGB",
            "Telepeizza"
    };

    public static final String[] ORIGINS = new String[]{
            "Albacete, Spain",
			"Tarragona, Spain",
			"Lugo, Spain",
			"Toledo, Spain",
			"Palencia, Spain",
			"Melilla, Spain",
			"Segovia, Spain",
			"Teruel, Spain",
            "Badajoz, Spain",
            "Murcia, Spain",
            "Burgos, Spain",
            "Granada, Spain",
            "Santander, Spain"
    };

    public static final String[] DESTS = new String[]{
            "Bilbao, Spain",
            "Madrid, Spain",
            "Barcelona, Spain",
            "Sevilla, Spain",
			"Valencia, Spain",
			"Alicante, Spain",
			"Zaragoza, Spain",
			"Lisboa, Portugal"
    };

    public static final String[] VEHICLE_TYPES = new String[] {
            "Tractocamión",
            "Furgoneta",
            "Camión articulado",
            "Trailer",
            "Tren de carretera",
            "Volqueta",
            "Tolva",
            "Camión jaula granel",
            "Cama baja",
            "Caja cerrada de 53 pies",
            "Caja cerrada de 48 pies",
            "Rabon",
            "Tortón",
            "Plataforma",
            "Autotanque",
            "Camión jaula enlonada",
            "Autotanque para asfalto",
            "Pick up",
            "SUV"
    };

    public static final String[] PKG_DESCS = new String[] {
		"Refrescos con gas",
		"Mobiliario",
		"Elementos ornamentales",
		"Material sanitario",
		"Pequeños electrodomésticos",
		"Discos vinilo",
		"Fruta fresca",
		"Carne congelada",
		"Animales vivos",
		"Pescado congelado",
		"Material de limpieza",
		"Grandes electrodomésticos"
    };

    public static final String[] OPERATOR_NAMES = new String[]{
		"M. Troisgros",
		"Y. Alleno",
		"J. Roca",
		"A. Donckele",
		"P. Gagnaire",
		"E. Renaut",
		"E. Crippa",
		"P. Barbot",
		"S. Yamamoto",
		"A. Ducasse",
		"M. Berasategui",
		"A. Aduriz",
		"J. Alija",
		"C. Ruscalleda",
		"V. Arguinzoniz",
		"E. Arzak",
		"E. Atxa",
		"Q. Dacosta",
		"J. Arzak"
    };

}

