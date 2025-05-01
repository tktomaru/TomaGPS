// ui/dashboard/LogListFragment.kt
package jp.tukutano.tomagps.ui.dashboard

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import jp.tukutano.tomagps.R
import jp.tukutano.tomagps.databinding.FragmentLogListBinding
import jp.tukutano.tomagps.ui.detail.LogDetailActivity
import kotlinx.coroutines.flow.collectLatest

class LogListFragment : Fragment(R.layout.fragment_log_list) {

    private var _binding: FragmentLogListBinding? = null
    private val binding get() = _binding!!
    private val vm: LogListViewModel by viewModels()

    // ← ここを TrackPointAdapter から LogAdapter に変更
    private val adapter = LogAdapter { log ->
        // 詳細画面へ遷移
        val intent = Intent(requireContext(), LogDetailActivity::class.java).apply {
            putExtra(LogDetailActivity.EXTRA_LOG_ID, log.id)
        }
        startActivity(intent)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _binding = FragmentLogListBinding.bind(view)

        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            setHasFixedSize(true)
            adapter = this@LogListFragment.adapter
        }

        // JourneyLog のリストを流し込む
        viewLifecycleOwner.lifecycleScope.launchWhenStarted {
            vm.logs.collectLatest { list ->
                adapter.submitList(list)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
