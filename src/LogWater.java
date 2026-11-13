import java.sql.*;

public class LogWater {

    private Connection conn;

    public LogWater(Connection conn) {
        this.conn = conn;
    }

    // Application supports both liters and gallons, but only liters will be entered into db.
    public String enter(int userId, double amount, boolean isLiter) {
        // water should be >= 0
        if (amount <= 0) throw new Error("Negative amount.");

        // check for SQL connection
        try {
            if (conn.isClosed()) throw new Error("Connection is not open.");
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        
        String fullName;

        // convert to liters, truncate to two places
        if (!isLiter) amount = amount * 3.785412;
        amount = Math.floor(amount * 100) / 100;

        try {
            PreparedStatement checkForUser = conn.prepareStatement("SELECT firstName, lastName FROM USERS WHERE user_id = ? LIMIT 1");
            PreparedStatement addAmount = conn.prepareStatement("INSERT INTO WATER_LOG (userId, amount) VALUES (?, ?)");

            checkForUser.setInt(1, userId);
            ResultSet res = checkForUser.executeQuery();
            if (!res.next()) throw new Error("User doesn't exist.");
            fullName = res.getString("firstName") + " " + res.getString("lastName");

            addAmount.setInt(1, userId);
            addAmount.setFloat(2, (float) amount);
            addAmount.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        return "Added " + amount + "L to " + fullName;
    }
}