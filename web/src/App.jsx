import { useRoute } from './router.js';
import Home from './Home.jsx';
import Control from './Control.jsx';

export default function App() {
  const [path, go] = useRoute();
  const m = path.match(/^\/play\/([\w-]+)\/?$/);
  if (m) return <Control key={m[1]} id={m[1]} go={go} />;
  return <Home go={go} />;
}
