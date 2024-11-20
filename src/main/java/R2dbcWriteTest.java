import io.r2dbc.spi.*;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.function.Supplier;

public class R2dbcWriteTest {

    public static void main(String[] args) {
        var options = ConnectionFactoryOptions.builder()
                .option(Option.valueOf("allowPublicKeyRetrieval"), true)
                .option(Option.valueOf("useSSL"), false)
                .option(ConnectionFactoryOptions.HOST, System.getenv("HOST"))
                .option(ConnectionFactoryOptions.PORT, Integer.valueOf(System.getenv("PORT")))
                .option(ConnectionFactoryOptions.DATABASE, System.getenv("DB"))
                .option(ConnectionFactoryOptions.USER, System.getenv("USER"))
                .option(ConnectionFactoryOptions.PASSWORD, System.getenv("PASSWORD"))
                .option(ConnectionFactoryOptions.SSL, false)
                .build();

        for (var connectionFactoryProvider : List.<Supplier<ConnectionFactoryProvider>>of(
                dev.miku.r2dbc.mysql.MySqlConnectionFactoryProvider::new,
                org.mariadb.r2dbc.MariadbConnectionFactoryProvider::new,
                io.asyncer.r2dbc.mysql.MySqlConnectionFactoryProvider::new
        )) {
            var connectionFactory = connectionFactoryProvider.get().create(options);
            System.out.println(connectionFactory.getClass().getCanonicalName());
            for (TransactionDefinition tx : List.of(
                    tx(IsolationLevel.READ_COMMITTED, null),
                    tx(IsolationLevel.READ_UNCOMMITTED, null),
                    tx(IsolationLevel.REPEATABLE_READ, null),
                    tx(IsolationLevel.SERIALIZABLE, null),

                    tx(IsolationLevel.READ_COMMITTED, false),
                    tx(IsolationLevel.READ_UNCOMMITTED, false),
                    tx(IsolationLevel.REPEATABLE_READ, false),
                    tx(IsolationLevel.SERIALIZABLE, false),

                    tx(IsolationLevel.READ_COMMITTED, true),
                    tx(IsolationLevel.READ_UNCOMMITTED, true),
                    tx(IsolationLevel.REPEATABLE_READ, true),
                    tx(IsolationLevel.SERIALIZABLE, true)
            )) {
                System.out.println(tx);
                try {
                    Mono.from(connectionFactory.create())
                            .flatMap(con ->
                                    Mono.from(con.beginTransaction(tx))
                                            .thenMany(con.createStatement("insert into test values(?, ?);")
                                                    .bind(0, connectionFactory.getClass().getCanonicalName())
                                                    .bind(1, tx.toString())
                                                    .execute())
                                            .then(Mono.from(con.commitTransaction()))
                                            .then(Mono.from(con.close()))
                            )


                            .block();
                } catch (Exception e) {
                    System.out.println("ERROR: " + e.getMessage());
                }
            }
            System.out.println();
        }
    }

    private static TransactionDefinition tx(IsolationLevel isolationLevel, Boolean readOnly) {
        return new TransactionDefinition() {
            @Override
            public <T> T getAttribute(Option<T> option) {
                if (option.equals(READ_ONLY)) {
                    return (T) readOnly;
                }
                return isolationLevel.getAttribute(option);
            }

            @Override
            public String toString() {
                return (readOnly == null ? "" : (readOnly ? "ro-" : "rw-")) + isolationLevel;
            }
        };
    }
}
