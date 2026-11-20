package booking;

import booking.BookingResult.Booked;
import booking.BookingResult.Rejected;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Interactive REPL over {@link BookingService}. State lives for the whole session, since the store is in memory.
 * The CLI only splits lines and checks argument counts; all validation happens in the service.
 */
public final class Main {

    private static final String HELP = """
            Commands:
              book <providerId> <start> <end>   book [start, end) for a provider; timestamps like 2026-11-20T10:00:00Z
              cancel <appointmentId>            cancel an appointment, freeing its slot
              list <providerId>                 show all of a provider's appointments, active and cancelled
              help                              show this help
              exit                              quit (Ctrl-D also works)""";

    private final BookingService service;
    private final PrintStream out;

    Main(BookingService service, PrintStream out) {
        this.service = service;
        this.out = out;
    }

    public static void main(String[] args) throws IOException {
        Main cli = new Main(new BookingService(new InMemoryAppointmentStore()), System.out);
        cli.run(new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8)));
    }

    /** Reads commands until "exit" or end of input. */
    void run(BufferedReader in) throws IOException {
        out.println("Appointment booking. Type 'help' for commands.");
        while (true) {
            out.print("> ");
            out.flush();
            String line = in.readLine();
            if (line == null) {
                return; // end of input (Ctrl-D)
            }
            String[] args = line.trim().split("\\s+");
            if (args[0].isEmpty()) {
                continue; // blank line
            }
            if (!handle(args)) {
                return;
            }
        }
    }

    /** Runs one command. Returns false when the session should end. */
    private boolean handle(String[] args) {
        switch (args[0]) {
            case "book" -> {
                if (args.length != 4) {
                    out.println("usage: book <providerId> <start> <end>");
                } else {
                    printBooking(service.book(args[1], args[2], args[3]));
                }
            }
            case "cancel" -> {
                if (args.length != 2) {
                    out.println("usage: cancel <appointmentId>");
                } else {
                    out.println(service.cancel(args[1]));
                }
            }
            case "list" -> {
                if (args.length != 2) {
                    out.println("usage: list <providerId>");
                } else {
                    printList(args[1], service.listForProvider(args[1]));
                }
            }
            case "help" -> out.println(HELP);
            case "exit" -> {
                out.println("bye");
                return false;
            }
            default -> out.println("unknown command '" + args[0] + "'. Type 'help' for commands.");
        }
        return true;
    }

    private void printBooking(BookingResult result) {
        switch (result) {
            case Booked booked -> {
                Appointment a = booked.appointment();
                out.println("BOOKED  id=" + a.id() + "  " + a.providerId() + "  " + a.start() + " to " + a.end());
            }
            case Rejected rejected -> out.println("REJECTED  " + rejected.reason() + ": " + rejected.message());
        }
    }

    private void printList(String providerId, List<Appointment> appointments) {
        if (appointments.isEmpty()) {
            out.println("no appointments for " + providerId);
            return;
        }
        for (Appointment a : appointments) {
            out.println(a.id() + "  " + a.start() + " to " + a.end() + "  " + a.status());
        }
    }
}
