package mate.jdbc.dao.impl;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import mate.jdbc.dao.CarDao;
import mate.jdbc.exception.DataProcessingException;
import mate.jdbc.lib.Dao;
import mate.jdbc.model.Car;
import mate.jdbc.model.Driver;
import mate.jdbc.model.Manufacturer;
import mate.jdbc.util.ConnectionUtil;

@Dao
public class CarDaoImpl implements CarDao {
    private static final int CAR_ID_INDEX = 1;
    private static final int MANUFACTURER_NAME_INDEX = 6;
    private static final int DRIVER_ID_INDEX = 11;
    private static final int DRIVER_NAME_INDEX = 12;
    private static final int DRIVER_LICENCE_NUMBER_INDEX = 13;

    @Override
    public Car create(Car car) {
        String query = "INSERT INTO cars (model, manufacturer_id)"
                + " VALUES (?, ?);";
        try (Connection connection = ConnectionUtil.getConnection();
                PreparedStatement statement =
                        connection.prepareStatement(query, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, car.getModel());
            statement.setLong(2, car.getManufacturer().getId());
            statement.executeUpdate();
            ResultSet resultSet = statement.getGeneratedKeys();
            if (resultSet.next()) {
                car.setId(resultSet.getObject(1, Long.class));
            }
        } catch (SQLException e) {
            throw new DataProcessingException("Can't create car: " + car, e);
        }
        try {
            insertDrivers(car);
            return car;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Optional<Car> get(Long id) {
        String query = "SELECT * FROM cars c"
                + " JOIN manufacturers m ON c.manufacturer_id = m.id"
                + " JOIN car_drivers cd ON c.id = cd.car_id"
                + " JOIN drivers d ON d.id = cd.driver_id"
                + " WHERE c.is_deleted = FALSE AND c.id = ?";
        Car car = null;
        try (Connection connection = ConnectionUtil.getConnection();
                PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setLong(1, id);
            ResultSet resultSet = statement.executeQuery();
            if (resultSet.next()) {
                car = getCar(resultSet);
            }
            return Optional.ofNullable(car);
        } catch (SQLException e) {
            throw new DataProcessingException("Can't get car by id: " + id, e);
        }
    }

    @Override
    public List<Car> getAll() {
        List<Car> cars = new LinkedList<>();
        String query = "SELECT * FROM cars c"
                + " JOIN manufacturers m ON c.manufacturer_id = m.id"
                + " JOIN car_drivers cd ON c.id = cd.car_id"
                + " JOIN drivers d ON d.id = cd.driver_id"
                + " WHERE c.is_deleted = FALSE";;
        try (Connection connection = ConnectionUtil.getConnection();
                PreparedStatement statement = connection.prepareStatement(query)) {
            ResultSet resultSet = statement.executeQuery();
            while (resultSet.next()) {
                cars.add(getCar(resultSet));
            }
            return cars;
        } catch (SQLException e) {
            throw new DataProcessingException("Can't get all cars from DB", e);
        }
    }

    @Override
    public List<Car> getAllByDriver(long driverId) {
        String query = "SELECT * FROM cars c"
                + " JOIN manufacturers m ON c.manufacturer_id = m.id"
                + " JOIN car_drivers cd ON c.id = cd.car_id"
                + " JOIN drivers d ON d.id = cd.driver_id"
                + " WHERE c.is_deleted = FALSE AND d.id = ?";;
        List<Car> cars = new LinkedList<>();
        try (Connection connection = ConnectionUtil.getConnection();
                PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setLong(1, driverId);
            ResultSet resultSet = statement.executeQuery();
            while (resultSet.next()) {
                cars.add(getCar(resultSet));
            }
            return cars;
        } catch (SQLException e) {
            throw new DataProcessingException("Can't get all cars by driver id: " + driverId, e);
        }
    }

    @Override
    public Car update(Car car) {
        String query = "UPDATE cars SET model = ?, manufacturer_id = ?"
                + " WHERE id = ? AND is_deleted = FALSE;";
        try (Connection connection = ConnectionUtil.getConnection();
                PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, car.getModel());
            statement.setLong(2, car.getManufacturer().getId());
            statement.setLong(3, car.getId());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new DataProcessingException("Can't update car: " + car, e);
        }
        try {
            deleteDrivers(car.getId());
            insertDrivers(car);
            return car;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean delete(Long id) {
        String query = "UPDATE cars SET is_deleted = TRUE WHERE id = ?;";
        try (Connection connection = ConnectionUtil.getConnection();
                PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setLong(1, id);
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DataProcessingException("Can't delete car by id: " + id, e);
        }
    }

    private void insertDrivers(Car car) throws SQLException {
        String query = "INSERT INTO car_drivers (driver_id, car_id) VALUES (?, ?);";
        try (Connection connection = ConnectionUtil.getConnection();
                PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setLong(2, car.getId());
            for (Driver driver: car.getDrivers()) {
                statement.setLong(1, driver.getId());
                statement.executeUpdate();
            }
        }
    }

    private void deleteDrivers(Long id) throws SQLException {
        String query = "DELETE FROM car_drivers WHERE car_id = ?;";
        try (Connection connection = ConnectionUtil.getConnection();
                PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setLong(1, id);
            statement.executeUpdate();
        }
    }

    private static List<Driver> getDrivers(ResultSet resultSet) throws SQLException {
        List<Driver> drivers = new LinkedList<>();
        while (resultSet.next()) {
            Driver driver = new Driver(resultSet.getObject(DRIVER_ID_INDEX, Long.class),
                    resultSet.getString(DRIVER_NAME_INDEX),
                    resultSet.getString(DRIVER_LICENCE_NUMBER_INDEX));
            drivers.add(driver);
        }
        return drivers;
    }

    private static Car getCar(ResultSet resultSet) throws SQLException {
        Manufacturer manufacturer =
                new Manufacturer(resultSet.getObject("manufacturer_id", Long.class),
                        resultSet.getString(MANUFACTURER_NAME_INDEX),
                        resultSet.getString("country"));
        return new Car(resultSet.getObject(CAR_ID_INDEX, Long.class),
                resultSet.getString("model"), manufacturer,
                getDrivers(resultSet));
    }
}
