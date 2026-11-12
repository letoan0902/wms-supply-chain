import { RobotOutlined } from '@ant-design/icons'
import { Button } from 'antd'
import { useNavigate } from 'react-router-dom'
import { useCan } from '../../auth/useCan'
import { DATA_TYPES } from '../aiImport'
import type { ExtractionDataType } from '../types'

/**
 * Nút "Nhập bằng AI" trên trang danh mục: mở màn hình Nhập dữ liệu bằng AI với loại dữ liệu chọn sẵn. Chỉ hiện với
 * vai trò được nhập loại dữ liệu đó.
 */
export default function AiImportButton({ dataType }: { dataType: ExtractionDataType }) {
  const can = useCan()
  const navigate = useNavigate()
  if (!can(DATA_TYPES[dataType].capability)) return null
  return (
    <Button icon={<RobotOutlined className="text-blue-600" />} onClick={() => navigate(`/ai-import?type=${dataType}`)}>
      Nhập bằng AI
    </Button>
  )
}
