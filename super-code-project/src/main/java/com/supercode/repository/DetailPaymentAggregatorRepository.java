package com.supercode.repository;

import com.supercode.request.GeneralRequest;
import com.supercode.util.MessageConstant;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@ApplicationScoped
public class DetailPaymentAggregatorRepository implements PanacheRepository<com.supercode.entity.DetailPaymentAggregator> {

    @PersistenceContext
    EntityManager entityManager;
    public int getCountDataAggregator(GeneralRequest request,String branchId, List<BigDecimal> grossAmounts) {
        String query ="select count(*) from detail_agregator_payment dpos " +
                "where trans_date = ?1  " +
                "and gross_amount IN (?2) and pm_id = ?3 and branch_id = ?4 " +
                "and flag_rekon_pos ='0'";
        if (request.getTransTime() != null && !request.getTransTime().isEmpty()) {
            query += "AND SUBSTRING(trans_time, 1, 2) = :transTime ";
        }
        query+="  order by trans_date, trans_time asc";

        Query nativeQuery = entityManager.createNativeQuery(
                        query)
                .setParameter(1, request.getTransDate())
                .setParameter(2, grossAmounts) // Menggunakan IN dengan ()
                .setParameter(3, request.getPmId())
                .setParameter(4, branchId);
        if (request.getTransTime() != null && !request.getTransTime().isEmpty()) {
            nativeQuery.setParameter("transTime", request.getTransTime().substring(0, 2));
        }

        Object result = nativeQuery.getSingleResult();
        return ((Number) result).intValue();
    }

   /* public void updateFlagByCondition(GeneralRequest request, List<BigDecimal> grossAmounts) {
        // Jika Anda ingin menggunakan BigDecimal, maka parameter "grossAmounts" sebaiknya menggunakan tipe BigDecimal
        String query = "UPDATE detail_agregator_payment dpos " +
                "SET flag_rekon_pos = :newFlag " +
                "WHERE trans_date = :transDate " +
                "AND SUBSTRING(trans_time, 1, 2) = :transTime " +
                "AND gross_amount IN :grossAmounts " +  // Menggunakan parameter untuk 'IN'
                "AND pm_id = :pmId " +
                "AND branch_id = :branchId " +
                "AND flag_rekon_pos = '0'";

        entityManager.createNativeQuery(query)
                .setParameter("newFlag", "1")
                .setParameter("transDate", request.getTransDate())
                .setParameter("transTime", request.getTransTime())
                .setParameter("grossAmounts", grossAmounts)  // Menggunakan parameter list untuk IN
                .setParameter("pmId", request.getPmId())
                .setParameter("branchId", request.getBranchId())
                .executeUpdate();
    }*/

    /*public void updateFlagByCondition(GeneralRequest request, List<BigDecimal> grossAmounts) {
        String newFlag= MessageConstant.TWO_VALUE;
        // Langkah 1: Identifikasi data unik berdasarkan gross_amount dengan batas jumlah yang sama di POS
        String findUniqueQuery = "SELECT detail_payment_id FROM ( " +
                "    SELECT dap1.detail_payment_id, dap1.gross_amount, " +
                "           ROW_NUMBER() OVER (PARTITION BY dap1.gross_amount ORDER BY dap1.detail_payment_id) AS rn " +
                "    FROM detail_agregator_payment dap1 " +
                "    WHERE dap1.trans_date = :transDate " +
                "    AND dap1.pm_id = :pmId " +
                "    AND dap1.branch_id = :branchId " +
                "    AND dap1.flag_rekon_pos = '0' " +
                "    AND dap1.gross_amount IN (:grossAmounts) order by dap1.trans_date, dap1.trans_time asc" +
                ") temp WHERE rn <= ( " +
                "    SELECT COUNT(*) FROM detail_point_of_sales pos " +
                "    WHERE pos.trans_date = :transDate " +
                "    AND pos.pm_id = :pmId " +
                "    AND pos.branch_id = :branchId " +
                "    AND pos.gross_amount = temp.gross_amount order by pos.trans_date, pos.trans_time asc" +
                ")";

        if (request.getTransTime() != null && !request.getTransTime().isEmpty()) {
            findUniqueQuery += " AND SUBSTRING(trans_time, 1, 2) = :transTime";
            newFlag= MessageConstant.ONE_VALUE;
        }

        Query uniqueQuery = entityManager.createNativeQuery(findUniqueQuery)
                .setParameter("transDate", request.getTransDate())
                .setParameter("pmId", request.getPmId())
                .setParameter("branchId", request.getBranchId())
                .setParameter("grossAmounts", grossAmounts);

        if (request.getTransTime() != null && !request.getTransTime().isEmpty()) {
            uniqueQuery.setParameter("transTime", request.getTransTime().substring(0, 2));
        }

        List<BigDecimal> uniqueIds = uniqueQuery.getResultList();

        // Langkah 2: Update hanya jumlah yang sesuai dengan data POS
        if (!uniqueIds.isEmpty()) {
            String updateQuery = "UPDATE detail_agregator_payment " +
                    "SET flag_rekon_pos =  " + newFlag+
                    " WHERE detail_payment_id IN (:uniqueIds)";

            Query updateNativeQuery = entityManager.createNativeQuery(updateQuery)
                    .setParameter("uniqueIds", uniqueIds);

            updateNativeQuery.executeUpdate();
        }
    }*/

    public void updateFlagByCondition(GeneralRequest request, List<BigDecimal> grossAmounts) {
        String newFlag = MessageConstant.TWO_VALUE;
        // Langkah 1: Identifikasi data unik berdasarkan gross_amount dengan batas jumlah yang sama di POS
        String findUniqueQuery = "SELECT detail_payment_id FROM ( " +
                "    SELECT detail_payment_id, trans_date, trans_time FROM ( " +
                "         SELECT dap1.detail_payment_id, dap1.gross_amount, dap1.trans_date, dap1.trans_time, " +
                "                ROW_NUMBER() OVER (PARTITION BY dap1.gross_amount " +
                "                                   ORDER BY dap1.trans_date, dap1.trans_time, dap1.detail_payment_id) AS rn " +
                "         FROM detail_agregator_payment dap1 " +
                "         WHERE dap1.trans_date = :transDate " +
                "           AND dap1.pm_id = :pmId " +
                "           AND dap1.branch_id = :branchId " +
                "           AND dap1.flag_rekon_pos = '0' " +
                "           AND dap1.gross_amount IN (:grossAmounts) ";

        if (request.getTransTime() != null && !request.getTransTime().isEmpty()) {
            findUniqueQuery += " AND SUBSTRING(dap1.trans_time, 1, 2) = :transTime ";
            newFlag = MessageConstant.ONE_VALUE;
        }

        findUniqueQuery += "    ) innerQuery " +
                "    WHERE rn <= ( " +
                "         SELECT COUNT(*) FROM detail_point_of_sales pos " +
                "         WHERE pos.trans_date = :transDate " +
                "           AND pos.pay_method_aggregator = :pmId " +
                "           AND pos.branch_id = :branchId " +
                "           and pos.flag_rekon_ecom='0'" +
                "           AND pos.gross_amount = innerQuery.gross_amount ";

        if (request.getTransTime() != null && !request.getTransTime().isEmpty()) {
            findUniqueQuery += " AND SUBSTRING(pos.trans_time, 1, 2) = :transTime ";
        }

        findUniqueQuery += "         ) " +
                "    ORDER BY trans_date, trans_time " +
                ") ordered";

        Query uniqueQuery = entityManager.createNativeQuery(findUniqueQuery)
                .setParameter("transDate", request.getTransDate())
                .setParameter("pmId", request.getPmId())
                .setParameter("branchId", request.getBranchId())
                .setParameter("grossAmounts", grossAmounts);

        if (request.getTransTime() != null && !request.getTransTime().isEmpty()) {
            uniqueQuery.setParameter("transTime", request.getTransTime().substring(0, 2));
        }

        List<BigDecimal> uniqueIds = uniqueQuery.getResultList();

        // Langkah 2: Update hanya data yang sesuai dengan kondisi dari POS
        if (!uniqueIds.isEmpty()) {
            String updateQuery = "UPDATE detail_agregator_payment " +
                    "SET flag_rekon_pos = " + newFlag +
                    " WHERE detail_payment_id IN (:uniqueIds)";

            Query updateNativeQuery = entityManager.createNativeQuery(updateQuery)
                    .setParameter("uniqueIds", uniqueIds);

            updateNativeQuery.executeUpdate();
        }
    }





    public String getTransDateByParentId(String parentId) {
        return entityManager.createNativeQuery(
                        "select distinct trans_date from detail_agregator_payment " +
                                "where parent_id = ?1 ")
                .setParameter(1, parentId)
                .getSingleResult().toString();
    }

    public void updateFlagNormalByCondition(GeneralRequest request, List<BigDecimal> grossAmounts) {
        String newFlag= MessageConstant.TWO_VALUE;
        String query = "UPDATE detail_agregator_payment dpos " +
                "SET flag_rekon_pos = :newFlag " +
                "WHERE trans_date = :transDate " +
                "AND gross_amount IN :grossAmounts " +  // Menggunakan parameter untuk 'IN'
                "AND pm_id = :pmId " +
                "AND branch_id = :branchId " +
                "AND flag_rekon_pos = '0' ";

        if (request.getTransTime() != null && !request.getTransTime().isEmpty()) {
            query += "AND SUBSTRING(trans_time, 1, 2) = :transTime ";
            newFlag= MessageConstant.ONE_VALUE;
        }
        query += " order by trans_date, trans_time asc";

       Query nativeQuery =  entityManager.createNativeQuery(query)
                .setParameter("newFlag", newFlag)
                .setParameter("transDate", request.getTransDate())
                .setParameter("grossAmounts", grossAmounts)  // Menggunakan parameter list untuk IN
                .setParameter("pmId", request.getPmId())
                .setParameter("branchId", request.getBranchId());
        if (request.getTransTime() != null && !request.getTransTime().isEmpty()) {
            nativeQuery.setParameter("transTime", request.getTransTime().substring(0, 2));
        }
        nativeQuery.executeUpdate();
    }

    public List<BigDecimal> getAllGrossAmount(GeneralRequest request) {
        String query ="select gross_amount from detail_agregator_payment dpos " +
                "where trans_date = ?1   " +
                "and pm_id = ?2 and branch_id =?3 and flag_rekon_pos ='0' ";
        if (request.getTransTime() != null && !request.getTransTime().isEmpty()) {
            query += "AND SUBSTRING(trans_time, 1, 2) = :transTime ";
        }
        query += " order by trans_date, trans_time asc";

        Query nativeQuery = entityManager.createNativeQuery(
                        query)
                .setParameter(1, request.getTransDate())
                .setParameter(2, request.getPmId())
                .setParameter(3, request.getBranchId());
        if (request.getTransTime() != null && !request.getTransTime().isEmpty()) {
            nativeQuery.setParameter("transTime", request.getTransTime().substring(0, 2));
        }

        List<BigDecimal> result = nativeQuery.getResultList();
        return result;
    }

    public int getFailedRecon(String parentId) {
        Object result = entityManager.createNativeQuery(
                        "select count(*) from detail_agregator_payment dpos " +
                                "where parent_id = ?1  and flag_rekon_pos=0 order by trans_date, trans_time asc")
                .setParameter(1, parentId)
                .getSingleResult();

        return ((Number) result).intValue();
    }

    public int getCountDataAggregatorByBranch(GeneralRequest request, List<BigDecimal> grossAmounts) {
        String query ="select count(*) from detail_agregator_payment dpos " +
                "where trans_date = ?1  " +
                "and gross_amount IN (?2)  and branch_id = ?3 " +
                "and flag_rekon_pos ='0'";

        Query nativeQuery = entityManager.createNativeQuery(
                        query)
                .setParameter(1, request.getTransDate())
                .setParameter(2, grossAmounts) // Menggunakan IN dengan ()
                .setParameter(3, request.getBranchId());
        Object result = nativeQuery.getSingleResult();
        return ((Number) result).intValue();
    }

    public List<BigDecimal> getAllGrossAmountByBranch(GeneralRequest request) {
        String query ="select gross_amount from detail_agregator_payment dpos " +
                "where trans_date = ?1   " +
                " and branch_id =?2 and flag_rekon_pos ='0' ";

        Query nativeQuery = entityManager.createNativeQuery(
                        query)
                .setParameter(1, request.getTransDate())
                .setParameter(2, request.getBranchId());
        List<BigDecimal> result = nativeQuery.getResultList();
        return result;
    }

    public void updateFlagByBranchCondition(GeneralRequest request, List<BigDecimal> grossAmounts) {
        String newFlag = MessageConstant.THREE_VALUE;
        // Langkah 1: Identifikasi data unik berdasarkan gross_amount dengan batas jumlah yang sama di POS
        String findUniqueQuery = "SELECT detail_payment_id FROM ( " +
                "    SELECT detail_payment_id, trans_date, trans_time FROM ( " +
                "         SELECT dap1.detail_payment_id, dap1.gross_amount, dap1.trans_date, dap1.trans_time, " +
                "                ROW_NUMBER() OVER (PARTITION BY dap1.gross_amount " +
                "                                   ORDER BY dap1.trans_date, dap1.trans_time, dap1.detail_payment_id) AS rn " +
                "         FROM detail_agregator_payment dap1 " +
                "         WHERE dap1.trans_date = :transDate " +
                "           AND dap1.branch_id = :branchId " +
                "           AND dap1.flag_rekon_pos = '0' " +
                "           AND dap1.gross_amount IN (:grossAmounts) ";


        findUniqueQuery += "    ) innerQuery " +
                "    WHERE rn <= ( " +
                "         SELECT COUNT(*) FROM detail_point_of_sales pos " +
                "         WHERE pos.trans_date = :transDate " +
                "           AND pos.branch_id = :branchId " +
                "           AND pos.gross_amount = innerQuery.gross_amount ";

        findUniqueQuery += "         ) " +
                "    ORDER BY trans_date, trans_time " +
                ") ordered";

        Query uniqueQuery = entityManager.createNativeQuery(findUniqueQuery)
                .setParameter("transDate", request.getTransDate())
                .setParameter("branchId", request.getBranchId())
                .setParameter("grossAmounts", grossAmounts);



        List<BigDecimal> uniqueIds = uniqueQuery.getResultList();

        // Langkah 2: Update hanya data yang sesuai dengan kondisi dari POS
        if (!uniqueIds.isEmpty()) {
            String updateQuery = "UPDATE detail_agregator_payment " +
                    "SET flag_rekon_pos = " + newFlag +
                    " WHERE detail_payment_id IN (:uniqueIds)";

            Query updateNativeQuery = entityManager.createNativeQuery(updateQuery)
                    .setParameter("uniqueIds", uniqueIds);

            updateNativeQuery.executeUpdate();
        }
    }

    public void updateFlagNormalByBranchCondition(GeneralRequest request, List<BigDecimal> grossAmounts) {
        String newFlag= MessageConstant.THREE_VALUE;
        String query = "UPDATE detail_agregator_payment dpos " +
                "SET flag_rekon_pos = :newFlag " +
                "WHERE trans_date = :transDate " +
                "AND gross_amount IN :grossAmounts " +
                "AND branch_id = :branchId " +
                "AND flag_rekon_pos = '0' ";

        query += " order by trans_date, trans_time asc";

        Query nativeQuery =  entityManager.createNativeQuery(query)
                .setParameter("newFlag", newFlag)
                .setParameter("transDate", request.getTransDate())
                .setParameter("grossAmounts", grossAmounts)
                .setParameter("branchId", request.getBranchId());
        if (request.getTransTime() != null && !request.getTransTime().isEmpty()) {
            nativeQuery.setParameter("transTime", request.getTransTime().substring(0, 2));
        }
        nativeQuery.executeUpdate();
    }
}
