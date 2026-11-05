package com.librarymanagementsystem.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.librarymanagementsystem.model.Book;
import com.librarymanagementsystem.model.Loan;
import com.librarymanagementsystem.model.Member;
import com.librarymanagementsystem.service.LibraryManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class JsonManager {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    private JsonManager() {
    }

    public static void saveState(LibraryManager library, String fileName)
            throws IOException {

        StateFile state = new StateFile();

        for (Book book : library.getBooks().values()) {
            BookData data = new BookData();
            data.itemId = book.getItemId();
            data.title = book.getTitle();
            data.author = book.getAuthor();
            data.isbn = book.getIsbn();
            data.available = book.isAvailable();
            state.books.add(data);
        }

        for (Member member : library.getMembers().values()) {
            MemberData data = new MemberData();
            data.userId = member.getUserId();
            data.name = member.getName();
            data.email = member.getEmail();
            data.phone = member.getPhone();
            data.active = member.isActive();
            state.members.add(data);
        }

        for (Loan loan : library.getLoans()) {
            LoanData data = new LoanData();
            data.loanId = loan.getLoanId();
            data.bookId = loan.getBook().getItemId();
            data.memberId = loan.getMember().getUserId();
            data.issueDate = loan.getIssueDate().toString();
            data.dueDate = loan.getDueDate().toString();

            if (loan.getReturnDate() != null) {
                data.returnDate = loan.getReturnDate().toString();
            }

            state.loans.add(data);
        }

        Path path = Path.of(fileName);
        Path parent = path.toAbsolutePath().getParent();

        if (parent != null) {
            Files.createDirectories(parent);
        }

        MAPPER.writeValue(path.toFile(), state);

        System.out.println("Library data saved to " + path);
    }

    public static void loadState(String fileName, LibraryManager library)
            throws IOException {

        Path path = Path.of(fileName);

        if (!Files.exists(path)) {
            throw new IOException("State file not found: " + path);
        }

        StateFile state = MAPPER.readValue(path.toFile(), StateFile.class);

        if (state == null || state.books == null
                || state.members == null || state.loans == null) {
            throw new IOException(
                    "JSON file is missing books, members, or loans");
        }

        List<Book> books = new ArrayList<>();
        Map<Integer, Book> booksById = new HashMap<>();

        List<Member> members = new ArrayList<>();
        Map<Integer, Member> membersById = new HashMap<>();

        List<Loan> loans = new ArrayList<>();

        try {
            for (BookData data : state.books) {

                if (data == null) {
                    throw new IOException("JSON contains an empty book record");
                }

                Book book = new Book(
                        data.itemId, data.title, data.author, data.isbn);

                book.setAvailable(data.available);

                if (booksById.putIfAbsent(book.getItemId(), book) != null) {
                    throw new IOException(
                            "Duplicate book ID: " + book.getItemId());
                }

                books.add(book);
            }

            for (MemberData data : state.members) {

                if (data == null) {
                    throw new IOException("JSON contains an empty member record");
                }

                Member member = new Member(
                        data.userId, data.name, data.email, data.phone);

                member.setActive(data.active);

                if (membersById.putIfAbsent(member.getUserId(), member) != null) {
                    throw new IOException(
                            "Duplicate member ID: " + member.getUserId());
                }

                members.add(member);
            }

            for (LoanData data : state.loans) {

                if (data == null || data.issueDate == null
                        || data.dueDate == null) {
                    throw new IOException(
                            "JSON contains an incomplete loan record");
                }

                Book book = booksById.get(data.bookId);
                Member member = membersById.get(data.memberId);

                if (book == null || member == null) {
                    throw new IOException(
                            "Loan " + data.loanId
                                    + " refers to a missing book or member");
                }

                Loan loan = new Loan(
                        data.loanId,
                        book,
                        member,
                        LocalDate.parse(data.issueDate),
                        LocalDate.parse(data.dueDate));

                if (data.returnDate != null && !data.returnDate.isBlank()) {
                    loan.markReturned(LocalDate.parse(data.returnDate));
                }

                loans.add(loan);
            }

            library.restoreState(books, members, loans);

        } catch (RuntimeException e) {
            throw new IOException(
                    "Invalid data in " + path + ": " + e.getMessage(), e);
        }

        System.out.println("Library data loaded from " + path);
    }

    public static class StateFile {
        public List<BookData> books = new ArrayList<>();
        public List<MemberData> members = new ArrayList<>();
        public List<LoanData> loans = new ArrayList<>();
    }

    public static class BookData {
        public int itemId;
        public String title;
        public String author;
        public String isbn;
        public boolean available;
    }

    public static class MemberData {
        public int userId;
        public String name;
        public String email;
        public String phone;
        public boolean active;
    }

    public static class LoanData {
        public int loanId;
        public int bookId;
        public int memberId;
        public String issueDate;
        public String dueDate;
        public String returnDate;
    }
}
