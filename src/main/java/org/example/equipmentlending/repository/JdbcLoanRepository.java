package org.example.equipmentlending.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.ArrayList;

import org.example.equipmentlending.model.Loan;
import org.example.equipmentlending.model.LoanStatus;
import org.example.equipmentlending.model.Staff;
import org.example.equipmentlending.model.Student;
import org.example.equipmentlending.model.Item;
import org.example.equipmentlending.model.ItemCondition;
import org.example.equipmentlending.model.Member;
import org.example.equipmentlending.model.MemberType;


public class JdbcLoanRepository implements LoanRepository{
    private final StudentRepository studentRepository;
    private final ItemRepository itemRepository;
    private final StaffRepository staffRepository;
    private final TeacherRepository teacherRepository;
    private static final String FIND_BY_STUDENT_ID = """
    SELECT id, student_id, item_id, registered_by_id,
        borrow_date, due_date, status,
        returned_by_id, return_processed_by_id,
        return_date, loss_reported_date,
        return_condition, return_description
    FROM loans
    WHERE student_id = ?
    ORDER BY borrow_date, id
    """;
    private final Connection connection;

    public JdbcLoanRepository(Connection connection) {
        if (connection == null) {
            throw new IllegalArgumentException(
                "Connection must not be null."
            );
        }

        this.connection = connection;
        this.studentRepository = new JdbcStudentRepository(connection);
        this.itemRepository = new JdbcItemRepository(connection);
        this.staffRepository = new JdbcStaffRepository(connection);
        this.teacherRepository = new JdbcTeacherRepository(connection);
    }
    @Override
    public List<Loan> findByStudentId(int studentId) {
        if (studentId <= 0) {
            throw new IllegalArgumentException("Student ID must be positive.");
        }

        List<Loan> loans = new ArrayList<>();

        try (PreparedStatement statement =
                connection.prepareStatement(FIND_BY_STUDENT_ID)) {
            statement.setInt(1, studentId);

            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    loans.add(readLoan(result));
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException(
                "Could not load loans for student " + studentId, exception
            );
        }

        return loans;
    }
    private Loan readLoan(ResultSet result) throws SQLException {
        int loanId = result.getInt("id");
        int studentId = result.getInt("student_id");

        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new IllegalStateException(
                        "Loan " + loanId + " references missing student " + studentId
                ));
        int itemId = result.getInt("item_id")
        Item item = itemRepository.findById(itemId).orElseThrow(
            () -> new IllegalStateException("Loan" + loanId + " reference missing item "+ itemId));
        int registeredById = result.getInt("registered_by_id");
        Staff registeredBy = staffRepository.findById(registeredById)
                .orElseThrow(() -> new IllegalStateException("Loan" + loanId + " reference missing staff "+ registeredById));
        LocalDate borrowDate = result.getObject("borrow_date",LocalDate.class);
        LocalDate dueDate = result.getObject("due_date",LocalDate.class);
        LoanStatus status = LoanStatus.valueOf(result.getString("status"));
        LocalDate returnDate =
                result.getObject("return_date", LocalDate.class);
        LocalDate lossReportedDate =
                result.getObject("loss_reported_date", LocalDate.class);
        String returnDescription = result.getString("return_description");
        String conditionText = result.getString("return_condition");
        ItemCondition returnCondition = conditionText == null ? null : ItemCondition.valueOf(conditionText);
        Integer returnProcessedById = result.getObject("return_processed_by_id",Integer.class);
        Staff returnProcessedBy = returnProcessedById == null ? null : staffRepository.findById(returnProcessedById)
                .orElseThrow(() -> new IllegalStateException("Loan " + loanId + " references missing staff " + returnProcessedById));
    }
    private Member readMember(int memberId) throws SQLException {
        String sql = """
            SELECT member_type
            FROM members
            WHERE id = ?
            """;
        MemberType type;
        try (PreparedStatement statement =
                connection.prepareStatement(sql)) {

            statement.setInt(1, memberId);
            try (ResultSet result = statement.executeQuery()){
                if(!result.next()){
                    throw new IllegalStateException("Member not found "+ memberId);
                }
                type = MemberType.valueOf(result.getString("member_type"));
            }
        }
        String table = switch (type) {
            case STUDENT -> "students";
            case STAFF -> "staff";
            case TEACHER -> "teachers";
        };
        String lookupSql = "SELECT id FROM " + table + " WHERE member_id = ?";
        int personId;
        try (PreparedStatement statement = connection.prepareStatement(lookupSql)) {
            statement.setInt(1, memberId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new IllegalStateException(
                        "Member " + memberId + " has no matching record in " + table
                    );
                }
                personId = result.getInt("id");
            }
        }
        return switch (type) {
            case STUDENT -> studentRepository.findById(personId)
                    .orElseThrow(() -> new IllegalStateException(
                            "Student not found: " + personId));

            case STAFF -> staffRepository.findById(personId)
                    .orElseThrow(() -> new IllegalStateException(
                            "Staff not found: " + personId));

            case TEACHER -> teacherRepository.findById(personId)
                    .orElseThrow(() -> new IllegalStateException(
                            "Teacher not found: " + personId));
        };
    }
}
