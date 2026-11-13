package org.example.equipmentlending.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.util.List;
import java.util.ArrayList;
import java.util.Optional;

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

    public JdbcLoanRepository(Connection connection){
        if(connection == null){
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
    public Optional<Loan> findById(int loanId){
        if (loanId <=0){
            throw new IllegalArgumentException("Loan id must be strictly positive.");
        }
        String sql = """
            SELECT id, student_id, item_id, registered_by_id,
                borrow_date, due_date, status,
                returned_by_id, return_processed_by_id,
                return_date, loss_reported_date,
                return_condition, return_description
            FROM loans
            WHERE id = ?
            """;
        try (PreparedStatement statement =
                connection.prepareStatement(sql)){

            statement.setInt(1, loanId);
            try (ResultSet result = statement.executeQuery()){
                if(!result.next()){
                    return Optional.empty();
                }
                Loan loan = readLoan(result);
                return Optional.of(loan);
            }
        }
        catch (SQLException exception){
            throw new IllegalStateException("Could not find loan with ID " + loanId, exception);
        }
    }
    @Override
    public List<Loan> findByStudentId(int studentId){
        if(studentId <= 0){
            throw new IllegalArgumentException("Student ID must be positive.");
        }

        List<Loan> loans = new ArrayList<>();

        try (PreparedStatement statement =
                connection.prepareStatement(FIND_BY_STUDENT_ID)){
            statement.setInt(1, studentId);

            try (ResultSet result = statement.executeQuery()){
                while (result.next()){
                    loans.add(readLoan(result));
                }
            }
        } catch (SQLException exception){
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
        int itemId = result.getInt("item_id");
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
        Integer returnedById = result.getObject("returned_by_id",Integer.class);
        Member returnedBy = returnedById == null ? null : readMember(returnedById);
        return Loan.restore(loanId,student,item,registeredBy,borrowDate,dueDate,status,returnedBy,returnProcessedBy,returnDate,lossReportedDate,returnCondition,returnDescription);
    }
    private Member readMember(int memberId) throws SQLException {
        String sql = """
            SELECT member_type
            FROM members
            WHERE id = ?
            """;
        MemberType type;
        try (PreparedStatement statement =
                connection.prepareStatement(sql)){

            statement.setInt(1, memberId);
            try (ResultSet result = statement.executeQuery()){
                if(!result.next()){
                    throw new IllegalStateException("Member not found "+ memberId);
                }
                type = MemberType.valueOf(result.getString("member_type"));
            }
        }
        String table = switch (type){
            case STUDENT -> "students";
            case STAFF -> "staff";
            case TEACHER -> "teachers";
        };
        String lookupSql = "SELECT id FROM " + table + " WHERE member_id = ?";
        int personId;
        try (PreparedStatement statement = connection.prepareStatement(lookupSql)){
            statement.setInt(1, memberId);
            try (ResultSet result = statement.executeQuery()){
                if(!result.next()){
                    throw new IllegalStateException(
                        "Member " + memberId + " has no matching record in " + table
                    );
                }
                personId = result.getInt("id");
            }
        }
        return switch (type){
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
    private int findMemberId(Member member) throws SQLException {
        MemberType type = member.getType();
        String table = switch (type){
            case STUDENT -> "students";
            case STAFF -> "staff";
            case TEACHER -> "teachers";
        };
        String sql = "SELECT member_id FROM "+table+" WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)){
            statement.setInt(1, member.getId());
            try (ResultSet result = statement.executeQuery()){
                if(!result.next()){
                    throw new IllegalStateException(
                        "No record in  " + table + " for id " + member.getId()
                    );
                }
                return result.getInt("member_id");
            }
        }
    }
    private void bindLoanFields(PreparedStatement statement,int startIndex,Loan loan,Integer returnedByMemberId) throws SQLException{
        int index = startIndex;

        statement.setInt(index++, loan.getStudent().getId());
        statement.setInt(index++, loan.getItem().getId());
        statement.setInt(index++, loan.getRegisteredBy().getId());

        statement.setObject(index++, loan.getBorrowDate(), Types.DATE);
        statement.setObject(index++, loan.getDueDate(), Types.DATE);

        statement.setString(index++, loan.getStatus().name());

        if (returnedByMemberId == null) {
            statement.setNull(index++, Types.INTEGER);
        } else {
            statement.setInt(index++, returnedByMemberId);
        }

        if (loan.getReturnProcessedBy() == null) {
            statement.setNull(index++, Types.INTEGER);
        } else {
            statement.setInt(index++, loan.getReturnProcessedBy().getId());
        }

        if (loan.getReturnDate() == null) {
            statement.setNull(index++, Types.DATE);
        } else {
            statement.setObject(index++, loan.getReturnDate(), Types.DATE);
        }

        if (loan.getLossReportedDate() == null) {
            statement.setNull(index++, Types.DATE);
        } else {
            statement.setObject(index++, loan.getLossReportedDate(), Types.DATE);
        }

        if (loan.getReturnCondition() == null) {
            statement.setNull(index++, Types.VARCHAR);
        } else {
            statement.setString(index++, loan.getReturnCondition().name());
        }

        if (loan.getReturnDescription() == null) {
            statement.setNull(index++, Types.VARCHAR);
        } else {
            statement.setString(index++, loan.getReturnDescription());
        }
    }
    @Override
    public void save(Loan loan){
        if(loan == null){
            throw new IllegalArgumentException("Loan must not be null.");
        }
        try {
            if(connection.getAutoCommit()){
                throw new IllegalStateException(
                    "Loan saving requires a caller-managed transaction."
                );
            }
            Integer returnedByMemberId = loan.getReturnedBy() == null ? null : findMemberId(loan.getReturnedBy());
            String sql = """
                INSERT INTO loans (
                id, student_id, item_id, registered_by_id,
                borrow_date, due_date, status,
                returned_by_id, return_processed_by_id,
                return_date, loss_reported_date,
                return_condition, return_description
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
            student_id = ?,
            item_id = ?,
            registered_by_id = ?,
            borrow_date = ?,
            due_date = ?,
            status = ?,
            returned_by_id = ?,
            return_processed_by_id = ?,
            return_date = ?,
            loss_reported_date = ?,
            return_condition = ?,
            return_description = ?
            """;
            try(PreparedStatement statement = connection.prepareStatement(sql)){
                statement.setInt(1, loan.getId());
                bindLoanFields(statement, 2, loan, returnedByMemberId);
                bindLoanFields(statement, 14, loan, returnedByMemberId);
                statement.executeUpdate();
            }
        } 
        catch (SQLException exception){
            throw new IllegalStateException("Could not save loan with ID " + loan.getId(), exception);
        }
    }
}
