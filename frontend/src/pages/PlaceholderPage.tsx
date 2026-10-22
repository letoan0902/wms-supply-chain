import { ToolOutlined } from '@ant-design/icons'
import { Card, Result } from 'antd'

interface PlaceholderPageProps {
  title: string
  sprint: number
}

/** Trang giữ chỗ cho các chức năng chưa phát triển. */
export default function PlaceholderPage({ title, sprint }: PlaceholderPageProps) {
  return (
    <Card>
      <Result
        icon={<ToolOutlined />}
        title={title}
        subTitle={`Đang phát triển trong Sprint ${sprint}`}
      />
    </Card>
  )
}
