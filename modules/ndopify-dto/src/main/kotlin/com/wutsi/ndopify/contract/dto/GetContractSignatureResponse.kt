import com.wutsi.ndopify.contract.dto.ContractSignature

data class GetContractSignatureResponse(
    val signature: ContractSignature = ContractSignature(),
)
