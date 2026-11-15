import java.sql.*;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.Assert;

public class LogWaterTest {

    private Connection conn;
    private LogWater logger;

    @Before
    public void setUp() throws SQLException {
        conn = DriverManager.getConnection("jdbc:mysql://localhost:3306/cometfit", "cometfit", "cometfit");
        conn.createStatement().executeUpdate("DELETE FROM WATER_LOG");
        logger = new LogWater(conn);
    }

    @After
    public void tearDown() throws SQLException {
        conn.close();
    }
    
    @Test
    public void testInputGallons() {
        String msg = logger.enter(1, 0.3, false);
        Assert.assertEquals("Added 1.13L to Jane Doe", msg);
    }

    @Test
    public void testNegativeAmount() {
        Error e = Assert.assertThrows(Error.class, () -> logger.enter(2, -0.3, true));
        Assert.assertEquals("Negative amount.", e.getMessage());
    }

    @Test
    public void testUnknownUser() {
        Error e = Assert.assertThrows(Error.class, () -> logger.enter(3, 0.5, true));
        Assert.assertEquals("User doesn't exist.", e.getMessage());
    }
}
